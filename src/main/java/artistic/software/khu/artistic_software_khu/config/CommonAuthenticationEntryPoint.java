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
 *
 * 어떤 에러 코드를 쓸지는 만들 때 받는다. 앱 체인은 UNAUTHORIZED 를,
 * 기기 체인은 DEVICE_UNAUTHORIZED 를 쓴다("API.md" 3-1 · 3-4). 두 체인이 같은
 * 코드를 쓰면 기기가 "재페어링이 필요한 상황" 과 "그냥 로그인이 필요한 상황" 을
 * 구분할 수 없다. 기기는 앞엣것을 받으면 스스로 복구할 방법이 없어 사용자에게
 * 재페어링을 안내해야 한다.
 *
 * 응답 "형태" 자체는 앱과 기기가 같다. 2026-09-09 에 기기 API 도 공통 envelope 를
 * 쓰기로 확정했다. 기기 펌웨어의 파서 부담보다 응답 처리 코드가 두 벌로
 * 갈라지는 비용이 크다고 보았다.
 */
public class CommonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	private final ErrorCode errorCode;

	public CommonAuthenticationEntryPoint(ObjectMapper objectMapper, ErrorCode errorCode) {
		this.objectMapper = objectMapper;
		this.errorCode = errorCode;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authenticationException) throws IOException {

		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		// 문구가 한국어라 인코딩을 지정하지 않으면 깨진 글자가 나간다
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(errorCode)));
	}

}
