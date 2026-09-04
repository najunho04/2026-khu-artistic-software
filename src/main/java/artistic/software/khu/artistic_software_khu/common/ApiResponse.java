package artistic.software.khu.artistic_software_khu.common;

import java.util.List;

/**
 * 모든 API 가 공유하는 응답 봉투. "API.md" 2장 공통 응답 포맷을 그대로 옮긴 것이다.
 *
 * 성공이든 실패든 항상 success / data / error 세 필드가 나간다.
 * 성공일 때 error 는 null 이고, 실패일 때 data 는 null 이다.
 * 필드를 빼지 않고 null 로 내보내는 이유는 앱이 매번 필드 존재 여부를 확인하지
 * 않아도 되게 하기 위해서다.
 *
 * 반환값이 없는 경우(삭제, 로그아웃 등)는 이 봉투를 쓰지 않고
 * HTTP 204 에 바디 없이 응답한다. "API.md" 2-1 의 규칙이다.
 *
 * 이 클래스를 개별 API 구현보다 먼저 만드는 이유는, 나중에 구현 대상 37개 API 의
 * 응답 형태를 한꺼번에 다시 손대는 일을 막기 위해서다.
 *
 * @param <T>     data 에 담기는 실제 응답 본문의 타입
 * @param success 성공 여부
 * @param data    성공 시의 응답 본문. 실패 시에는 null
 * @param error   실패 시의 오류 정보. 성공 시에는 null
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

	/**
	 * 성공 응답을 만든다.
	 */
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	/**
	 * 실패 응답을 만든다. details 가 없는 일반 오류용이다.
	 */
	public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
		return new ApiResponse<>(false, null, ApiError.of(errorCode));
	}

	/**
	 * 검증 실패 응답을 만든다. 어떤 필드가 왜 틀렸는지를 함께 담는다.
	 */
	public static <T> ApiResponse<T> failure(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return new ApiResponse<>(false, null, ApiError.of(errorCode, details));
	}

}
