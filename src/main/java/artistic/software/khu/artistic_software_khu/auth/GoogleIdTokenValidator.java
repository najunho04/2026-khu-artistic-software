package artistic.software.khu.artistic_software_khu.auth;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 구글 idToken 의 발급자(iss)와 대상(aud)을 검사한다.
 *
 * 서명 검증은 이 클래스가 하지 않는다. 그건 구글 공개키로 JwtDecoder 가 먼저
 * 끝내고, 여기서는 "서명이 진짜인 토큰의 내용이 우리 것이 맞는가" 만 따진다.
 *
 * aud 검사가 이 클래스의 존재 이유다. 구글이 발급한 토큰은 전 세계 모든 앱에
 * 대해 만들어진다. 그중 "우리 앱을 위해 만들어진 것" 만 받아야 한다. 이 검사가
 * 빠지면 남의 앱용으로 발급된 정상 구글 토큰으로도 우리 서버에 로그인된다.
 * 그 토큰은 서명도 iss 도 exp 도 전부 정상이라 다른 어떤 검사로도 걸러지지 않는다.
 */
public class GoogleIdTokenValidator implements OAuth2TokenValidator<Jwt> {

	// 구글은 iss 로 두 가지 형태를 모두 사용한다고 공식 문서에 밝히고 있다.
	// 한쪽만 허용하면 멀쩡한 로그인이 간헐적으로 실패한다.
	private static final Set<String> VALID_ISSUERS = Set.of(
		"https://accounts.google.com",
		"accounts.google.com"
	);

	private static final String INVALID_TOKEN_ERROR_CODE = "invalid_token";

	// 우리 앱의 구글 client ID 목록. iOS 와 안드로이드가 서로 다른 값을 쓴다.
	private final List<String> allowedClientIds;

	public GoogleIdTokenValidator(List<String> allowedClientIds) {
		this.allowedClientIds = List.copyOf(allowedClientIds);
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt idToken) {
		// 설정이 비어 있으면 전부 거부한다.
		// 설정을 깜빡했을 때 "아무나 통과" 가 되는 것보다 "아무도 통과 못 함" 이
		// 훨씬 낫다. 잘못 열려 있으면 아무도 모르지만, 안 되면 바로 발견된다.
		if (allowedClientIds.isEmpty()) {
			return failure("구글 client ID 가 설정되지 않아 idToken 을 검증할 수 없습니다.");
		}

		// getIssuer() 를 쓰지 않고 클레임을 문자열 그대로 읽는다.
		// getIssuer() 는 값을 URL 로 바꾸려 하는데, 구글이 쓰는 두 형태 중
		// 스킴이 없는 "accounts.google.com" 은 URL 로 변환되지 않기 때문이다.
		if (!VALID_ISSUERS.contains(idToken.getClaimAsString("iss"))) {
			return failure("구글이 발급한 idToken 이 아닙니다.");
		}

		// aud 는 값이 여러 개일 수 있어 목록으로 다룬다.
		// 그중 하나라도 우리 client ID 면 우리 앱을 위해 발급된 토큰이다.
		boolean issuedForOurApplication = idToken.getAudience().stream()
			.anyMatch(allowedClientIds::contains);

		if (!issuedForOurApplication) {
			return failure("다른 앱을 위해 발급된 idToken 입니다.");
		}

		return OAuth2TokenValidatorResult.success();
	}

	private static OAuth2TokenValidatorResult failure(String description) {
		return OAuth2TokenValidatorResult.failure(
			new OAuth2Error(INVALID_TOKEN_ERROR_CODE, description, null));
	}

}
