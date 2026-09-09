package artistic.software.khu.artistic_software_khu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청과 응답을 한 줄씩 로그로 남긴다. "ROADMAP.md" 1-2 의 마지막 항목이다.
 *
 * 무엇을 남기느냐보다 **무엇을 남기지 않느냐가 중요한 필터**다. 로깅 필터의
 * 위험은 동작하지 않는 것이 아니라, 잘 동작하면서 인증 정보를 평문으로 남기는
 * 것이다. 그 사고는 조용히 일어나고 몇 달 뒤 로그를 열어봤을 때야 드러난다.
 *
 * 그래서 세 가지 원칙을 지킨다.
 *
 * 1. **요청 본문을 아예 읽지 않는다.** 본문에는 비밀번호가 들어 있다. 읽어서
 *    필드별로 가리는 방법도 있지만, 그러려면 스트림을 먼저 읽고 다시 채워
 *    넣어야 하고 그 과정에서 실수하면 컨트롤러가 빈 본문을 받는다. 지금 본문을
 *    남겨야 할 이유가 없으므로 읽지 않는 쪽이 안전하다.
 * 2. **헤더는 정해진 것만 남긴다.** 전부 남기면 나중에 새 헤더가 생겼을 때
 *    아무도 모르는 사이에 로그로 흘러 들어간다.
 * 3. **쿼리 문자열도 가린다.** 페어링 코드가 쿼리로 올 일은 없어야 하지만,
 *    실수로 그런 API 가 하나라도 생기면 코드가 그대로 남는다.
 *
 * 가리는 규칙 자체는 "SensitiveValueMasker" 가 들고 있다. 규칙과 남기는 자리를
 * 나눠 두면 규칙을 HTTP 없이 검증할 수 있다.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

	// 로그에 남길 헤더. 인증 헤더는 마스커가 앞뒤 세 글자만 남긴다.
	// 통째로 가리지 않는 이유는 "어느 요청들이 한 사람의 것인가" 를 따라가야
	// 장애를 쫓을 수 있기 때문이다.
	private static final String[] LOGGED_HEADER_NAMES = {
		AccessUuidAuthenticationFilter.HEADER_NAME,
		DeviceAccessUuidAuthenticationFilter.HEADER_NAME
	};

	private final SensitiveValueMasker masker;

	public RequestLoggingFilter(SensitiveValueMasker masker) {
		this.masker = masker;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		long startedAt = System.currentTimeMillis();

		try {
			filterChain.doFilter(request, response);
		} finally {
			// finally 에 두는 이유는 예외가 났을 때야말로 로그가 가장 필요하기
			// 때문이다. try 없이 짜면 그 순간의 로그만 사라져서, 정작 원인을
			// 찾아야 할 때 아무것도 남지 않는다.
			//
			// 예외를 잡지 않고 finally 만 쓰는 것도 의도한 것이다. 여기서 삼키면
			// GlobalExceptionHandler 가 500 을 내보낼 기회를 잃고 응답이 200 으로 나간다.
			logger.info("{} {}{} -> {} ({}ms){}",
				request.getMethod(),
				request.getRequestURI(),
				maskedQueryString(request),
				response.getStatus(),
				System.currentTimeMillis() - startedAt,
				maskedHeaders(request));
		}
	}

	/**
	 * 쿼리 문자열의 값들을 필드 이름에 따라 가린다.
	 *
	 * 쿼리가 없으면 빈 문자열을 돌려준다. "?" 만 덩그러니 남는 것을 막기 위해서다.
	 */
	private String maskedQueryString(HttpServletRequest request) {
		String queryString = request.getQueryString();

		if (queryString == null || queryString.isBlank()) {
			return "";
		}

		String masked = Arrays.stream(queryString.split("&"))
			.map(this::maskQueryParameter)
			.collect(Collectors.joining("&"));

		return "?" + masked;
	}

	/**
	 * "이름=값" 한 쌍을 가린다. "=" 가 없는 조각은 그대로 둔다.
	 */
	private String maskQueryParameter(String parameter) {
		int separatorIndex = parameter.indexOf('=');

		if (separatorIndex < 0) {
			return parameter;
		}

		String name = parameter.substring(0, separatorIndex);
		String value = parameter.substring(separatorIndex + 1);

		return name + "=" + masker.mask(name, value);
	}

	/**
	 * 정해진 헤더만 골라 가린 값으로 이어 붙인다.
	 *
	 * 헤더가 하나도 없으면 빈 문자열을 돌려준다. 인증 없이 호출되는 경로
	 * (가입 · 로그인 · claim)에서 빈 대괄호가 붙는 것을 막기 위해서다.
	 */
	private String maskedHeaders(HttpServletRequest request) {
		String headers = Arrays.stream(LOGGED_HEADER_NAMES)
			.filter(headerName -> request.getHeader(headerName) != null)
			.map(headerName ->
				headerName + "=" + masker.mask(headerName, request.getHeader(headerName)))
			.collect(Collectors.joining(", "));

		return headers.isEmpty() ? "" : " [" + headers + "]";
	}

}
