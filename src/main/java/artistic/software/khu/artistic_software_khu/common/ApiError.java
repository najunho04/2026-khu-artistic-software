package artistic.software.khu.artistic_software_khu.common;

import java.util.List;

/**
 * 실패 응답의 error 객체. "API.md" 2-2 의 형태를 그대로 옮긴 것이다.
 *
 * code 와 message 를 함께 내보내지만 쓰임새가 다르다.
 * "API.md" 2-2 가 "앱은 message 가 아니라 code 로 분기해야 한다" 고 정했다.
 * message 는 사람에게 보여주는 문구라 언제든 다듬을 수 있고,
 * code 는 앱의 화면 분기 기준이라 바꾸면 앱이 깨진다.
 *
 * @param code    ErrorCode 상수 이름을 그대로 쓴 식별자
 * @param message 사용자 노출용 한국어 문구
 * @param details 검증 실패일 때만 채우고, 그 외에는 null 이다
 */
public record ApiError(String code, String message, List<FieldErrorDetail> details) {

	/**
	 * details 가 없는 일반 오류를 만든다. 대부분의 오류가 여기에 해당한다.
	 */
	public static ApiError of(ErrorCode errorCode) {
		return new ApiError(errorCode.name(), errorCode.getMessage(), null);
	}

	/**
	 * 필드별 사유를 함께 담는 검증 오류를 만든다.
	 */
	public static ApiError of(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return new ApiError(errorCode.name(), errorCode.getMessage(), details);
	}

}
