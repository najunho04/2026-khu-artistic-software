package artistic.software.khu.artistic_software_khu.config;

import artistic.software.khu.artistic_software_khu.auth.GoogleIdTokenValidator;
import artistic.software.khu.artistic_software_khu.auth.GoogleIdTokenVerifier;
import artistic.software.khu.artistic_software_khu.auth.SocialIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 구글 idToken 을 검증하는 실제 구현체를 조립한다.
 *
 * 검증은 두 단계로 나뉜다. JwtDecoder 가 구글 공개키로 서명을 확인하고,
 * 거기에 붙인 검사기들이 토큰 내용을 확인한다.
 *
 * 공개키는 구글이 공개하는 JWKS 주소에서 받아온다. NimbusJwtDecoder 는 이 키를
 * 받아 캐시해 두므로 로그인마다 구글을 호출하지 않는다. 키를 받아오는 시점도
 * 이 설정이 만들어질 때가 아니라 첫 검증이 일어날 때다. 덕분에 구글에 연결할 수
 * 없는 환경에서도 애플리케이션은 정상적으로 뜬다.
 */
@Configuration
@EnableConfigurationProperties(GoogleAuthenticationProperties.class)
public class GoogleAuthenticationConfiguration {

	private static final Logger logger =
		LoggerFactory.getLogger(GoogleAuthenticationConfiguration.class);

	// 구글이 idToken 서명에 쓰는 공개키를 공개하는 주소
	private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

	@Bean
	SocialIdTokenVerifier googleIdTokenVerifier(GoogleAuthenticationProperties properties) {
		if (properties.allowedClientIds().isEmpty()) {
			// 값을 넣지 않아도 애플리케이션은 뜨지만 구글 로그인은 전부 거부된다.
			// 뜨지 않게 막지 않는 이유는 로그인과 무관한 작업까지 못 하게 되기
			// 때문이고, 조용히 넘어가지 않는 이유는 설정을 깜빡한 것을 알아야
			// 하기 때문이다.
			logger.warn("yeso.auth.google.allowed-client-ids 가 비어 있어 구글 로그인이 모두 거부됩니다."
				+ " 앱의 iOS 와 안드로이드 client ID 를 설정하세요.");
		}

		return new GoogleIdTokenVerifier(googleIdTokenDecoder(properties));
	}

	private JwtDecoder googleIdTokenDecoder(GoogleAuthenticationProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder
			.withJwkSetUri(GOOGLE_JWK_SET_URI)
			.build();

		// 기본 검사기는 만료 시각을 본다. 거기에 우리가 만든 iss / aud 검사를 더한다.
		// 서명만 맞으면 통과시키면 안 된다. 구글이 발급한 토큰은 전 세계 모든 앱에
		// 대해 만들어지므로, 그중 우리 앱 것만 골라내는 aud 검사가 반드시 필요하다.
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
			JwtValidators.createDefault(),
			new GoogleIdTokenValidator(properties.allowedClientIds()));

		decoder.setJwtValidator(validator);

		return decoder;
	}

}
