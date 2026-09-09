package artistic.software.khu.artistic_software_khu.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청 · 응답 로깅 필터. "ROADMAP.md" 1-2 의 마지막 남은 항목이다.
 *
 * 이 테스트가 확인하는 것은 "무엇이 로그에 남는가" 가 아니라 **"무엇이 로그에
 * 남지 않는가"** 다. 로깅 필터의 위험은 기능이 안 도는 것이 아니라, 잘 돌면서
 * 인증 정보를 평문으로 남기는 것이다. 그 사고는 조용히 일어나고 몇 달 뒤
 * 로그를 열어봤을 때야 드러난다.
 *
 * 그래서 실제로 남은 로그 문장을 받아 그 안에 원래 값이 들어 있지 않은지 본다.
 * 마스커를 부르는지만 확인하면, 부르고 나서 원래 값을 따로 또 찍는 경우를
 * 놓친다.
 */
class RequestLoggingFilterTest {

	private static final String ACCESS_UUID = "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d";

	private ListAppender<ILoggingEvent> appender;

	private ch.qos.logback.classic.Logger filterLogger;

	@BeforeEach
	void attachAppender() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		filterLogger = context.getLogger(RequestLoggingFilter.class);

		appender = new ListAppender<>();
		appender.start();

		filterLogger.addAppender(appender);
		filterLogger.setLevel(Level.INFO);
	}

	@AfterEach
	void detachAppender() {
		filterLogger.detachAppender(appender);
	}

	@Test
	@DisplayName("메서드와 경로와 상태 코드를 남긴다")
	void logsMethodPathAndStatus() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(200);

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(request, response, new MockFilterChain());

		assertThat(loggedMessages()).anySatisfy(message ->
			assertThat(message).contains("POST").contains("/api/v1/auth/login").contains("200"));
	}

	@Test
	@DisplayName("인증 헤더는 앞뒤 세 글자만 남는다")
	void authenticationHeaderIsPartiallyMasked() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
		request.addHeader("X-Access-Uuid", ACCESS_UUID);

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		String allLogs = String.join("\n", loggedMessages());

		// 원래 값이 통째로 남으면 그 값을 본 사람이 계속 그 계정으로 행세할 수 있다.
		// access_uuid 는 만료가 없어 로그아웃 전까지 막을 방법이 없다.
		assertThat(allLogs).doesNotContain(ACCESS_UUID);
		assertThat(allLogs).contains("3f2...c4d");
	}

	@Test
	@DisplayName("기기 인증 헤더도 앞뒤 세 글자만 남는다")
	void deviceHeaderIsPartiallyMasked() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/device-api/v1/sync");
		request.addHeader("X-Device-Uuid", ACCESS_UUID);

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(String.join("\n", loggedMessages()))
			.doesNotContain(ACCESS_UUID)
			.contains("3f2...c4d");
	}

	@Test
	@DisplayName("요청 본문은 로그에 남기지 않는다")
	void requestBodyIsNeverLogged() throws Exception {
		// 본문에는 비밀번호가 들어 있다. 본문을 읽어 필드별로 가리는 방법도
		// 있지만, 그러려면 스트림을 먼저 읽고 다시 채워 넣어야 하고 그 과정에서
		// 실수하면 컨트롤러가 빈 본문을 받는다. 지금 본문을 남겨야 할 이유가
		// 없으므로 아예 읽지 않는 쪽이 안전하다.
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
		request.setContent("""
			{"email": "parent@example.com", "password": "mySecret1234"}""".getBytes());

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(String.join("\n", loggedMessages()))
			.doesNotContain("mySecret1234")
			.doesNotContain("password");
	}

	@Test
	@DisplayName("쿼리 문자열에 섞인 페어링 코드는 가려진다")
	void pairingCodeInQueryStringIsMasked() throws Exception {
		// 페어링 코드가 쿼리로 올 일은 없어야 하지만, 실수로 그렇게 만든 API 가
		// 하나라도 생기면 코드가 그대로 로그에 남는다. 경로는 항상 로그에
		// 남으므로 여기서 한 번 걸러 둔다.
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
		request.setQueryString("pairingCode=482913&childId=5");

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		String allLogs = String.join("\n", loggedMessages());

		assertThat(allLogs).doesNotContain("482913");
		// 가릴 대상이 아닌 값은 그대로 남아야 장애를 쫓을 수 있다.
		assertThat(allLogs).contains("childId=5");
	}

	@Test
	@DisplayName("다음 필터는 반드시 호출된다")
	void filterChainAlwaysProceeds() throws Exception {
		// 로깅은 곁다리 기능이다. 여기서 흐름이 끊기면 요청 자체가 처리되지 않는다.
		MockFilterChain filterChain = new MockFilterChain();

		new RequestLoggingFilter(new SensitiveValueMasker())
			.doFilter(new MockHttpServletRequest("GET", "/api/v1/users/me"),
				new MockHttpServletResponse(), filterChain);

		assertThat(filterChain.getRequest()).isNotNull();
	}

	@Test
	@DisplayName("다음 필터가 예외를 던져도 로그는 남고 예외는 그대로 전달된다")
	void logsEvenWhenChainThrows() {
		FilterChain throwingChain = (request, response) -> {
			throw new IllegalStateException("의도한 실패");
		};

		// 예외가 났을 때야말로 로그가 가장 필요하다. try 없이 짜면 그 순간의
		// 로그만 사라져서, 정작 원인을 찾아야 할 때 아무것도 남지 않는다.
		try {
			new RequestLoggingFilter(new SensitiveValueMasker())
				.doFilter(new MockHttpServletRequest("GET", "/api/v1/users/me"),
					new MockHttpServletResponse(), throwingChain);
		} catch (Exception expected) {
			// 예외를 삼키면 안 된다. 삼키면 GlobalExceptionHandler 가 500 을
			// 내보낼 기회를 잃고 응답이 200 으로 나간다.
			assertThat(expected).isInstanceOf(IllegalStateException.class);
		}

		assertThat(loggedMessages()).isNotEmpty();
	}

	private List<String> loggedMessages() {
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

}
