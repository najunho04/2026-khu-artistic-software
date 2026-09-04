package artistic.software.khu.artistic_software_khu.common;

/**
 * 업무 규칙을 어겼을 때 던지는 예외. 안에 ErrorCode 하나를 담는다.
 *
 * 예외 종류를 오류마다 만들지 않고 이 하나로 통일한 이유는, 무엇이 잘못됐는지를
 * 구분하는 정보가 이미 ErrorCode 에 다 들어 있기 때문이다. 예외 클래스를 늘리면
 * 전역 예외 처리기에 핸들러가 함께 늘어나는데, 그렇게 얻는 것이 없다.
 *
 * HTTP 상태 코드를 여기서 정하지 않는 것이 중요하다. 상태 코드는 ErrorCode 가
 * 들고 있고, 던지는 쪽은 "무엇이 잘못됐는지" 만 말한다. 그래야 같은 오류가
 * API 마다 다른 상태 코드로 나가는 일이 생기지 않는다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		// 로그에서 바로 알아볼 수 있도록 예외 메시지에 사용자 노출 문구를 그대로 쓴다
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

}
