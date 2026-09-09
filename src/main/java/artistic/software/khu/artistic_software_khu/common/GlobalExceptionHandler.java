package artistic.software.khu.artistic_software_khu.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 컨트롤러에서 터진 예외를 "API.md" 2-2 실패 포맷으로 바꿔 내보낸다.
 *
 * 이 처리기를 한 곳에 모아 두는 이유는, 오류 응답 형태를 컨트롤러마다 손으로
 * 만들면 같은 오류가 API 마다 다른 모양으로 나가기 때문이다. 앱은 code 로
 * 분기하므로 그 형태가 흔들리면 앱의 화면 분기가 통째로 어긋난다.
 *
 * 핸들러는 다섯 개다. 우리가 의도적으로 던진 업무 예외, 요청 검증 실패,
 * 없는 경로, 허용되지 않은 메서드, 그리고 나머지 전부다.
 *
 * 없는 경로와 메서드 불일치를 따로 잡는 이유는, 그러지 않으면 맨 아래의
 * "나머지 전부" 핸들러가 그것들까지 삼켜 500 으로 내보내기 때문이다.
 * 경로 오타 하나가 "서버가 고장났다" 로 보이면 장애 신고가 들어온다.
 * "API.md" 3-1 이 두 경우를 각각 NOT_FOUND 와 METHOD_NOT_ALLOWED 로 정의해 두었다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 우리가 의도적으로 던진 업무 예외를 처리한다.
	 *
	 * 상태 코드를 여기서 판단하지 않고 ErrorCode 가 들고 있는 값을 그대로 쓴다.
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();

		// 의도한 흐름이라 스택 트레이스까지 남기지 않는다. 어떤 코드였는지만 남긴다.
		logger.warn("업무 예외 발생: {}", errorCode.name());

		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(ApiResponse.failure(errorCode));
	}

	/**
	 * 요청 본문 검증(@Valid) 실패를 처리한다.
	 *
	 * 어느 필드가 왜 틀렸는지를 details 배열에 담는다. 사용자가 무엇을 고쳐야
	 * 하는지 알려면 "입력값이 올바르지 않습니다" 만으로는 부족하기 때문이다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(
		MethodArgumentNotValidException exception) {

		List<FieldErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
			.map(GlobalExceptionHandler::toFieldErrorDetail)
			.toList();

		logger.warn("요청 검증 실패: {}", details);

		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus())
			.body(ApiResponse.failure(ErrorCode.INVALID_INPUT, details));
	}

	/**
	 * 요청한 경로에 해당하는 것이 없을 때를 처리한다.
	 *
	 * 두 예외를 함께 잡는 이유는 스프링이 상황에 따라 다른 것을 던지기 때문이다.
	 * 정적 자원까지 뒤진 뒤 없으면 NoResourceFoundException 이,
	 * 핸들러 탐색 단계에서 없으면 NoHandlerFoundException 이 나온다.
	 * 밖에서 보면 둘 다 "그런 주소는 없다" 는 같은 뜻이다.
	 */
	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
		// 흔히 일어나는 일이라 스택 트레이스는 남기지 않는다.
		logger.warn("존재하지 않는 경로 요청: {}", exception.getMessage());

		return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
			.body(ApiResponse.failure(ErrorCode.NOT_FOUND));
	}

	/**
	 * 경로는 있지만 그 메서드는 받지 않는 경우를 처리한다.
	 *
	 * 404 와 나누는 이유는 앱 입장에서 고쳐야 할 것이 다르기 때문이다.
	 * 404 는 주소가 틀린 것이고, 405 는 주소는 맞는데 GET 과 POST 를
	 * 잘못 쓴 것이다.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
		HttpRequestMethodNotSupportedException exception) {

		logger.warn("허용되지 않은 메서드 요청: {}", exception.getMessage());

		return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
			.body(ApiResponse.failure(ErrorCode.METHOD_NOT_ALLOWED));
	}

	/**
	 * 위에서 잡히지 않은 모든 예외를 처리한다.
	 *
	 * 예외의 원래 메시지를 응답에 넣지 않는 것이 중요하다. 내부 구현이나 SQL 이
	 * 그대로 밖으로 새어 나갈 수 있기 때문이다. 사용자에게는 정해진 한국어 문구만
	 * 보여주고, 원인 파악에 필요한 내용은 로그에만 남긴다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		// 예상하지 못한 예외라 스택 트레이스까지 남긴다
		logger.error("처리되지 않은 예외 발생", exception);

		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
			.body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
	}

	/**
	 * 스프링의 필드 오류를 "API.md" 2-2 의 details 원소 형태로 바꾼다.
	 */
	private static FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
		String reason = fieldError.getDefaultMessage();
		return new FieldErrorDetail(fieldError.getField(), reason);
	}

}
