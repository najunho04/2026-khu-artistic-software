package artistic.software.khu.artistic_software_khu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 구글 idToken 의 iss 와 aud 검사를 확인한다.
 *
 * 서명 검증은 여기서 다루지 않는다. 그건 구글 공개키를 받아와야 하는 일이라
 * 네트워크를 타고, 우리가 만든 로직도 아니다. 여기서 확인하는 것은 "서명이 진짜인
 * 토큰이 들어왔을 때 그 내용이 우리 앱 것이 맞는지" 를 우리가 제대로 따지는가다.
 *
 * aud 검사가 특히 중요하다. 이 검사가 없으면 남의 앱용으로 발급된 "정상" 구글
 * 토큰으로도 우리 서버에 로그인할 수 있다. 서명도 iss 도 exp 도 전부 통과하기
 * 때문에 다른 어떤 검사로도 걸러지지 않는다.
 */
class GoogleIdTokenValidatorTest {

	private static final String IOS_CLIENT_ID = "111111.apps.googleusercontent.com";
	private static final String ANDROID_CLIENT_ID = "222222.apps.googleusercontent.com";

	private final GoogleIdTokenValidator validator =
		new GoogleIdTokenValidator(List.of(IOS_CLIENT_ID, ANDROID_CLIENT_ID));

	@Test
	@DisplayName("우리 iOS 앱으로 발급된 토큰은 통과한다")
	void tokenIssuedForOurIosClientPasses() {
		assertThat(validator.validate(jwt("https://accounts.google.com", IOS_CLIENT_ID))
			.hasErrors()).isFalse();
	}

	@Test
	@DisplayName("우리 안드로이드 앱으로 발급된 토큰도 통과한다")
	void tokenIssuedForOurAndroidClientPasses() {
		assertThat(validator.validate(jwt("https://accounts.google.com", ANDROID_CLIENT_ID))
			.hasErrors()).isFalse();
	}

	@Test
	@DisplayName("iss 가 https 없는 accounts.google.com 이어도 통과한다")
	void issuerWithoutSchemeIsAccepted() {
		// 구글은 두 가지 형태를 모두 쓴다고 문서에 명시하고 있다.
		// 한쪽만 허용하면 멀쩡한 로그인이 간헐적으로 실패한다.
		assertThat(validator.validate(jwt("accounts.google.com", IOS_CLIENT_ID))
			.hasErrors()).isFalse();
	}

	@Test
	@DisplayName("남의 앱으로 발급된 토큰은 거부한다")
	void tokenIssuedForAnotherApplicationIsRejected() {
		assertThat(validator.validate(jwt("https://accounts.google.com", "999999.apps.googleusercontent.com"))
			.hasErrors()).isTrue();
	}

	@Test
	@DisplayName("구글이 발급한 토큰이 아니면 거부한다")
	void tokenFromAnotherIssuerIsRejected() {
		assertThat(validator.validate(jwt("https://evil.example.com", IOS_CLIENT_ID))
			.hasErrors()).isTrue();
	}

	@Test
	@DisplayName("허용 client ID 가 하나도 설정되지 않으면 전부 거부한다")
	void everythingIsRejectedWhenNoClientIdConfigured() {
		// 설정을 깜빡했을 때 "아무나 통과" 가 아니라 "아무도 통과 못 함" 이 되어야 한다.
		// 잘못 열려 있는 것보다 안 되는 편이 훨씬 빨리 발견된다.
		GoogleIdTokenValidator emptyValidator = new GoogleIdTokenValidator(List.of());

		assertThat(emptyValidator.validate(jwt("https://accounts.google.com", IOS_CLIENT_ID))
			.hasErrors()).isTrue();
	}

	private static Jwt jwt(String issuer, String audience) {
		return Jwt.withTokenValue("검증에 쓰이지 않는 자리표시 값")
			.header("alg", "RS256")
			.claim("iss", issuer)
			.claim("aud", List.of(audience))
			.claim("sub", "google-user-0001")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.claims(claims -> claims.putAll(Map.of()))
			.build();
	}

}
