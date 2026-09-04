package artistic.software.khu.artistic_software_khu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 검증에 성공한 구글 idToken 에서 우리가 필요한 값을 뽑아내는지,
 * 실패했을 때 문서에 정해진 에러 코드로 바꾸는지 확인한다.
 *
 * JwtDecoder 를 생성자로 주입받게 만든 덕분에 이 테스트는 네트워크를 타지 않는다.
 * 실제 구현체는 구글 공개키를 받아오지만, 여기서는 정해진 결과를 돌려주는 가짜를
 * 끼운다. "단위 테스트와 CI 는 Mock, 실검증은 로컬" 이라는 T-3 정책 그대로다.
 */
class GoogleIdTokenVerifierTest {

	@Test
	@DisplayName("검증에 성공하면 구글 계정 식별자와 이메일, 이름을 돌려준다")
	void returnsSocialAccountWhenTokenIsValid() {
		GoogleIdTokenVerifier verifier = verifierReturning(
			jwtBuilder()
				.claim("sub", "google-user-0001")
				.claim("email", "parent@example.com")
				.claim("name", "김보호")
				.build());

		SocialAccount account = verifier.verify("어떤 idToken");

		// sub 가 USERS.provider_user_id 가 된다. 구글이 계정마다 부여하는 고유값이고
		// 이메일과 달리 바뀌지 않기 때문에 이것을 식별자로 쓴다.
		assertThat(account.providerUserId()).isEqualTo("google-user-0001");
		assertThat(account.email()).isEqualTo("parent@example.com");
		assertThat(account.name()).isEqualTo("김보호");
	}

	@Test
	@DisplayName("검증에 실패하면 AUTH_INVALID_ID_TOKEN 으로 바꾼다")
	void translatesDecoderFailureToDocumentedErrorCode() {
		// 서명이 틀렸든 만료됐든 aud 가 다르든, 앱 입장에서는 모두 "이 토큰으로는
		// 로그인할 수 없다" 하나다. 실패 사유를 밖으로 흘리지 않는 이유이기도 하다.
		JwtDecoder failingDecoder = token -> {
			throw new BadJwtException("서명이 올바르지 않습니다");
		};
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(failingDecoder);

		assertThatThrownBy(() -> verifier.verify("망가진 idToken"))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.AUTH_INVALID_ID_TOKEN);
	}

	@Test
	@DisplayName("sub 가 없으면 AUTH_INVALID_ID_TOKEN 으로 거부한다")
	void rejectsTokenWithoutSubject() {
		// sub 는 계정을 식별하는 유일한 값이다. 이게 없으면 어느 계정인지 알 수 없어
		// 가입도 조회도 할 수 없다. 정상 구글 토큰이라면 항상 들어 있다.
		GoogleIdTokenVerifier verifier = verifierReturning(
			jwtBuilder().claim("email", "parent@example.com").build());

		assertThatThrownBy(() -> verifier.verify("sub 없는 idToken"))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.AUTH_INVALID_ID_TOKEN);
	}

	@Test
	@DisplayName("이메일과 이름이 없어도 검증은 통과한다")
	void allowsTokenWithoutEmailAndName() {
		// 구글은 요청한 권한 범위에 따라 email 이나 name 을 주지 않을 수 있다.
		// 계정을 식별하는 데 필요한 것은 sub 하나뿐이라 나머지는 비어 있어도 받는다.
		// 다만 "API.md" 의 응답 예시에는 email 이 들어 있어서, 이메일이 없는 계정을
		// 어떻게 다룰지는 확인이 필요하다. ROADMAP 1-4 에 남겨두었다.
		GoogleIdTokenVerifier verifier = verifierReturning(
			jwtBuilder().claim("sub", "google-user-0002").build());

		SocialAccount account = verifier.verify("이메일 없는 idToken");

		assertThat(account.providerUserId()).isEqualTo("google-user-0002");
		assertThat(account.email()).isNull();
		assertThat(account.name()).isNull();
	}

	@Test
	@DisplayName("이 검증기가 담당하는 제공자는 구글이다")
	void supportsGoogleProvider() {
		assertThat(verifierReturning(jwtBuilder().claim("sub", "x").build()).getSupportedProvider())
			.isEqualTo(SocialProvider.GOOGLE);
	}

	private static GoogleIdTokenVerifier verifierReturning(Jwt decodedToken) {
		return new GoogleIdTokenVerifier(token -> decodedToken);
	}

	private static Jwt.Builder jwtBuilder() {
		return Jwt.withTokenValue("검증에 쓰이지 않는 자리표시 값")
			.header("alg", "RS256")
			.claim("iss", "https://accounts.google.com")
			.claim("aud", List.of("111111.apps.googleusercontent.com"))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600));
	}

}
