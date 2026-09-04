package artistic.software.khu.artistic_software_khu.auth;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 구글 idToken 을 직접 검증하는 구현체.
 *
 * 구글의 tokeninfo 엔드포인트를 호출하지 않고 직접 검증하는 이유는 세 가지다.
 * 첫째, 로그인마다 구글에 네트워크 왕복이 생기지 않는다. 둘째, 구글 API 가
 * 느리거나 죽어도 우리 로그인은 계속 된다. 셋째, CI 가 외부 네트워크를 타지
 * 않아야 한다는 T-4 정책을 지킬 수 있다.
 *
 * 실제 검증은 생성자로 받은 JwtDecoder 가 한다. 서명 확인(구글 공개키),
 * 만료 확인, 그리고 GoogleIdTokenValidator 의 iss/aud 확인까지가 그 안에서
 * 끝난다. 이 클래스가 하는 일은 그 결과를 우리 도메인 값으로 옮기고,
 * 실패를 문서에 정해진 에러 코드로 바꾸는 것이다.
 */
public class GoogleIdTokenVerifier implements SocialIdTokenVerifier {

	private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

	// 구글이 계정마다 부여하는 고유 식별자가 담기는 클레임 이름
	private static final String SUBJECT_CLAIM = "sub";

	private static final String EMAIL_CLAIM = "email";

	private static final String NAME_CLAIM = "name";

	private final JwtDecoder googleIdTokenDecoder;

	public GoogleIdTokenVerifier(JwtDecoder googleIdTokenDecoder) {
		this.googleIdTokenDecoder = googleIdTokenDecoder;
	}

	@Override
	public SocialProvider getSupportedProvider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public SocialAccount verify(String idToken) {
		Jwt verifiedToken;
		try {
			verifiedToken = googleIdTokenDecoder.decode(idToken);
		}
		catch (JwtException exception) {
			// 실패 사유는 로그에만 남긴다. 응답에 담으면 공격자에게 어느 검사를
			// 통과했고 어디서 걸렸는지 알려주는 셈이 된다.
			logger.warn("구글 idToken 검증 실패: {}", exception.getMessage());
			throw new BusinessException(ErrorCode.AUTH_INVALID_ID_TOKEN);
		}

		String providerUserId = verifiedToken.getClaimAsString(SUBJECT_CLAIM);

		// sub 는 계정을 식별하는 유일한 값이라 없으면 가입도 조회도 할 수 없다.
		// 정상적인 구글 토큰이라면 항상 들어 있으므로, 없다는 것은 우리가 예상한
		// 토큰이 아니라는 뜻이다.
		if (providerUserId == null || providerUserId.isBlank()) {
			logger.warn("구글 idToken 에 sub 클레임이 없습니다");
			throw new BusinessException(ErrorCode.AUTH_INVALID_ID_TOKEN);
		}

		// 이메일과 이름은 요청한 권한 범위에 따라 없을 수 있어 그대로 둔다.
		return new SocialAccount(
			providerUserId,
			verifiedToken.getClaimAsString(EMAIL_CLAIM),
			verifiedToken.getClaimAsString(NAME_CLAIM));
	}

}
