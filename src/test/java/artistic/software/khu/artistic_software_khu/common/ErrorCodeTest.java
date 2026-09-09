package artistic.software.khu.artistic_software_khu.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "API.md" 3장 에러 코드 표를 코드로 고정한다.
 *
 * 이 테스트가 존재하는 이유는 에러 코드가 앱의 분기 기준이기 때문이다.
 * "API.md" 2-2 가 "앱은 message 가 아니라 code 로 분기해야 한다" 고 못박고 있어서,
 * code 문자열이나 HTTP 상태가 조용히 바뀌면 앱의 화면 분기가 통째로 어긋난다.
 * 문서와 코드가 갈라지는 것을 사람 눈이 아니라 테스트가 잡게 한다.
 */
class ErrorCodeTest {

	// "API.md" 3장에 정의된 39개 중 커뮤니티 7개를 제외한 수.
	// 커뮤니티는 구현 제외라 그 코드를 던질 API 가 만들어지지 않는다.
	private static final int EXPECTED_ERROR_CODE_COUNT = 32;

	@Test
	@DisplayName("3-1 공통 에러 코드 6개가 문서와 같은 상태·문구를 가진다")
	void commonErrorCodesMatchDocument() {
		assertThat(ErrorCode.INVALID_INPUT.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ErrorCode.INVALID_INPUT.getMessage()).isEqualTo("입력값이 올바르지 않습니다.");

		assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorCode.UNAUTHORIZED.getMessage()).isEqualTo("로그인이 필요합니다.");

		assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(ErrorCode.NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

		assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(ErrorCode.INTERNAL_ERROR.getMessage()).isEqualTo("일시적인 오류가 발생했습니다.");
	}

	@Test
	@DisplayName("기기 인증 실패 코드는 401 이다")
	void deviceAuthenticationErrorCodeIsUnauthorized() {
		// 보안 필터 체인(1-2)에서 기기용 체인이 던질 코드다.
		// 기기 토큰 체계를 없애면서 DEVICE_TOKEN_INVALID 와 DEVICE_SECRET_INVALID 두 개가
		// DEVICE_UNAUTHORIZED 하나로 합쳐졌다. 토큰과 시크릿이 사라져 실패 사유를
		// 나눌 근거가 없어졌기 때문이다.
		assertThat(ErrorCode.DEVICE_UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorCode.DEVICE_UNAUTHORIZED.getMessage()).isEqualTo("기기 인증에 실패했습니다.");
	}

	@Test
	@DisplayName("3-2 인증 에러 코드가 문서와 같은 상태·문구를 가진다")
	void authenticationErrorCodesMatchDocument() {
		assertThat(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getMessage()).isEqualTo("이미 가입된 이메일입니다.");

		assertThat(ErrorCode.AUTH_INVALID_EMAIL_FORMAT.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

		assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.getMessage())
			.isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.");
	}

	@Test
	@DisplayName("이메일이 없을 때와 비밀번호가 틀릴 때는 같은 코드를 쓴다")
	void missingEmailAndWrongPasswordShareOneErrorCode() {
		// 두 경우를 구분해 알려주면 "이 이메일은 가입되어 있다" 는 사실이 새어 나간다.
		// 코드를 하나만 두면 구분해 응답할 자리 자체가 없어진다.
		assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
			.noneMatch(name -> name.equals("AUTH_EMAIL_NOT_FOUND")
				|| name.equals("AUTH_PASSWORD_MISMATCH"));
	}

	@Test
	@DisplayName("소셜 로그인과 토큰 만료 관련 코드는 남아 있지 않다")
	void socialLoginAndTokenExpiryErrorCodesAreRemoved() {
		// 로그인 방식이 이메일·비밀번호로 바뀌고 JWT 를 쓰지 않게 되면서
		// 이 코드들을 던질 곳이 사라졌다. 남겨두면 아무도 쓰지 않는 상수가 되어
		// "소셜 로그인이 아직 있나" 하는 오해를 부른다.
		assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
			.doesNotContain(
				"AUTH_INVALID_PROVIDER",
				"AUTH_INVALID_ID_TOKEN",
				"AUTH_TOKEN_EXPIRED",
				"AUTH_REFRESH_TOKEN_INVALID",
				"DEVICE_TOKEN_INVALID",
				"DEVICE_SECRET_INVALID");
	}

	@Test
	@DisplayName("루틴 반복 설정 에러 코드가 문서와 같은 상태를 가진다")
	void routineRepeatErrorCodesMatchDocument() {
		// 반복 모드 3종(RANGE/WEEKLY/DATES)이 들어오면서 추가된 코드다.
		assertThat(ErrorCode.ROUTINE_INVALID_REPEAT_RULE.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ErrorCode.ROUTINE_TOO_MANY_DATES.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("커뮤니티 에러 코드는 포함하지 않는다")
	void communityErrorCodesAreExcluded() {
		// 커뮤니티는 구현 제외이므로 이 코드들을 던질 API 가 없다.
		// 넣어두면 아무도 쓰지 않는 상수가 남아 "이 기능이 있나" 하는 오해를 부른다.
		assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
			.noneMatch(name -> name.startsWith("POST_")
				|| name.startsWith("COMMENT_")
				|| name.startsWith("LIKE_"));
	}

	@Test
	@DisplayName("캐릭터 에러 코드는 포함한다")
	void characterErrorCodeIsIncluded() {
		// CHARACTER_NOT_FOUND 는 "API.md" 3-6 에 커뮤니티와 같은 표에 들어 있지만
		// 캐릭터용이고 캐릭터는 구현 대상이므로 제외 대상이 아니다.
		assertThat(ErrorCode.CHARACTER_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("문서의 코드 개수와 열거형의 개수가 같다")
	void errorCodeCountMatchesDocument() {
		// 문서에 코드가 추가됐는데 열거형에 안 옮기는 일을 막는다.
		assertThat(ErrorCode.values()).hasSize(EXPECTED_ERROR_CODE_COUNT);
	}

}
