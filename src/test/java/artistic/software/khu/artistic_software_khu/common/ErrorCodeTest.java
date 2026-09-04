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
	void deviceAuthenticationErrorCodesAreUnauthorized() {
		// 보안 필터 체인(1-2)에서 기기용 체인이 던질 코드다.
		assertThat(ErrorCode.DEVICE_TOKEN_INVALID.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorCode.DEVICE_TOKEN_INVALID.getMessage()).isEqualTo("기기 인증에 실패했습니다.");
		assertThat(ErrorCode.DEVICE_SECRET_INVALID.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
