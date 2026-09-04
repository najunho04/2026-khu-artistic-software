package artistic.software.khu.artistic_software_khu.common;

import org.springframework.http.HttpStatus;

/**
 * "API.md" 3장 에러 코드 표를 그대로 옮긴 열거형.
 *
 * 각 상수가 "코드 문자열 + HTTP 상태 + 한국어 문구" 를 함께 들고 있는 이유는,
 * 컨트롤러마다 상태 코드를 손으로 적다가 같은 오류가 API 별로 다른 상태로
 * 나가는 일을 막기 위해서다. 코드 문자열은 상수 이름을 그대로 쓴다.
 *
 * "API.md" 2-2 가 "앱은 message 가 아니라 code 로 분기해야 한다" 고 정했으므로,
 * 상수 이름을 바꾸는 것은 앱의 화면 분기를 바꾸는 것과 같다. 문구만 다듬는 것과
 * 이름을 바꾸는 것은 무게가 다르다는 뜻이다.
 *
 * "API.md" 3-6 의 커뮤니티 코드 7종("POST_" / "COMMENT_" / "LIKE_" 접두사)은
 * 커뮤니티 도메인이 구현 제외이므로 여기에 넣지 않는다. 향후 구현하게 되면
 * 그 표를 그대로 옮긴다. 같은 표에 있는 CHARACTER_NOT_FOUND 는 캐릭터용이고
 * 캐릭터는 구현 대상이므로 포함한다.
 *
 * 문서와 이 열거형이 어긋나는 것은 ErrorCodeTest 가 잡는다.
 */
public enum ErrorCode {

	// "API.md" 3-1. 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

	// "API.md" 3-2. 인증 · 유저
	AUTH_INVALID_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
	AUTH_INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
	AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
	USER_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴한 계정입니다."),

	// "API.md" 3-3. 자녀
	CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다."),
	CHILD_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 자녀에 대한 권한이 없습니다."),
	CHILD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "등록 가능한 자녀 수를 초과했습니다."),

	// "API.md" 3-4. 기기 · 페어링
	DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기를 찾을 수 없습니다."),
	DEVICE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 기기에 대한 권한이 없습니다."),
	DEVICE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "자녀당 등록 가능한 기기 수를 초과했습니다."),
	PAIRING_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 페어링 코드입니다."),
	PAIRING_CODE_EXPIRED(HttpStatus.GONE, "페어링 코드가 만료되었습니다."),
	PAIRING_CODE_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 페어링 코드입니다."),
	DEVICE_UID_ALREADY_PAIRED(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 기기입니다."),
	DEVICE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "기기 인증에 실패했습니다."),
	DEVICE_SECRET_INVALID(HttpStatus.UNAUTHORIZED, "기기 인증 정보가 올바르지 않습니다."),

	// "API.md" 3-5. 루틴
	BIG_ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "루틴을 찾을 수 없습니다."),
	SMALL_ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),
	ROUTINE_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "고정 루틴을 찾을 수 없습니다."),
	ROUTINE_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "종료 시각이 시작 시각보다 빠를 수 없습니다."),
	ROUTINE_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "종료일이 시작일보다 빠를 수 없습니다."),
	ROUTINE_DATE_RANGE_TOO_LONG(HttpStatus.BAD_REQUEST, "한 번에 등록할 수 있는 기간을 초과했습니다."),
	ROUTINE_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "정렬 대상이 올바르지 않습니다."),

	// "API.md" 3-6. 커뮤니티 · 캐릭터
	CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;

	private final String message;

	ErrorCode(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getMessage() {
		return message;
	}

}
