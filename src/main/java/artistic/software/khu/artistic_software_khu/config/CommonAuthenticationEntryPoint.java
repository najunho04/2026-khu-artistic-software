package artistic.software.khu.artistic_software_khu.config;

import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증되지 않은 요청이 보호된 경로에 들어왔을 때 "API.md" 2-2 형태로 401 을 내보낸다.
 *
 * 이것이 따로 필요한 이유는 인증 실패가 컨트롤러에 닿기 전에 필터 단계에서
 * 끝나기 때문이다. 전역 예외 처리기는 컨트롤러에서 터진 예외만 잡으므로,
 * 여기서 직접 응답을 쓰지 않으면 인증 실패만 다른 모양으로 나가게 된다.
 * 앱은 code 로 분기하는데 그 code 가 없는 응답을 받게 되는 셈이다.
 */
public class CommonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public CommonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authenticationException) throws IOException {

		ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		// 문구가 한국어라 인코딩을 지정하지 않으면 깨진 글자가 나간다
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(errorCode)));
	}

}
