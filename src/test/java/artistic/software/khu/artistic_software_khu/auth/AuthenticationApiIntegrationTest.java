package artistic.software.khu.artistic_software_khu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "API.md" 4장 인증과 1-1 인증 규약을 실제 DB 를 띄워 확인한다.
 *
 * 실제 PostgreSQL 을 쓰는 이유는 여기서 확인하려는 것의 절반이 DB 제약이기
 * 때문이다. 이메일 중복 거절은 애플리케이션 검사만으로는 증명되지 않는다.
 * 검사와 삽입 사이에 다른 요청이 끼어드는 순간이 실제로 존재하므로 마지막
 * 방어선은 DB 의 유니크 제약이고, 그것이 정말 걸려 있는지는 넣어봐야 안다.
 *
 * 인증이 통과했는지를 "404 가 나오는가" 로 확인하는 대목이 여러 번 나온다.
 * 보호된 경로에 해당하는 컨트롤러가 아직 없기 때문이다. 보안 필터는 컨트롤러보다
 * 먼저 돌므로, 막히면 401 이고 통과하면 컨트롤러가 없어 404 가 난다.
 * 즉 "401 이 아니다" 가 곧 "필터를 통과했다" 는 뜻이다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationApiIntegrationTest {

	private static final String EMAIL = "parent@example.com";

	private static final String PASSWORD = "password1234";

	// "API.md" 1-1 이 정한 앱 인증 헤더.
	private static final String APP_HEADER = "X-Access-Uuid";

	// "API.md" 1-1 이 정한 기기 인증 헤더.
	private static final String DEVICE_HEADER = "X-Device-Uuid";

	// 보호된 앱 경로. 인증이 통과했는지 확인하는 데 쓴다.
	private static final String PROTECTED_APP_PATH = "/api/v1/users/me";

	// 보호된 기기 경로. 마찬가지로 컨트롤러가 없다.
	private static final String PROTECTED_DEVICE_PATH = "/device-api/v1/sync";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	// ------------------------------------------------------------------
	// 가입
	// ------------------------------------------------------------------

	@Test
	@DisplayName("가입하면 201 이고 accessUuid 를 받으며 이름은 아직 비어 있다")
	void signUpReturnsAccessUuidAndEmptyName() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessUuid").isNotEmpty())
			.andExpect(jsonPath("$.data.user.userId").isNumber())
			.andExpect(jsonPath("$.data.user.email").value(EMAIL))
			// 이름이 null 이면 앱은 온보딩 1차(보호자 성명 입력)로 분기한다.
			// 소셜 로그인 시절의 isFirstLogin 필드를 대신하는 신호다.
			.andExpect(jsonPath("$.data.user.name").doesNotExist())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("가입하면 USERS 에 한 행만 생긴다")
	void signUpCreatesExactlyOneRow() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD)).andExpect(status().isCreated());

		Integer rowCount = jdbcTemplate.queryForObject(
			"select count(*) from users where email = ?", Integer.class, EMAIL);

		assertThat(rowCount).isEqualTo(1);
	}

	@Test
	@DisplayName("비밀번호는 평문 그대로 저장되지 않는다")
	void passwordIsNotStoredAsPlainText() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD)).andExpect(status().isCreated());

		String storedHash = jdbcTemplate.queryForObject(
			"select password_hash from users where email = ?", String.class, EMAIL);

		// 평문과 같으면 해시를 아예 안 걸었다는 뜻이다.
		assertThat(storedHash).isNotNull().isNotEqualTo(PASSWORD);
		// bcrypt 결과는 "$2a$" 같은 접두사로 시작한다. 접두사를 확인하면
		// "무언가로 바꾸긴 했는데 되돌릴 수 있는 방식" 인 경우를 걸러낼 수 있다.
		assertThat(storedHash).startsWith("$2");
	}

	@Test
	@DisplayName("같은 이메일로 두 번 가입하면 409 다")
	void signUpWithDuplicateEmailIsRejected() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD)).andExpect(status().isCreated());

		mockMvc.perform(signUpRequest(EMAIL, "anotherPassword"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_ALREADY_EXISTS"));
	}

	@ParameterizedTest
	@DisplayName("이메일 형식이 아니면 400 이다")
	@ValueSource(strings = {"골뱅이없음", "@example.com", "parent@", "parent example@x.com"})
	void signUpWithMalformedEmailIsRejected(String malformedEmail) throws Exception {
		mockMvc.perform(signUpRequest(malformedEmail, PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_EMAIL_FORMAT"));
	}

	@Test
	@DisplayName("비밀번호가 8자 미만이면 400 이다")
	void signUpWithShortPasswordIsRejected() throws Exception {
		// 문자 조합 규칙은 두지 않고 길이만 본다. 조합 규칙은 기억하기 어려운
		// 비밀번호를 만들게 해 오히려 다른 곳에서 쓰던 것을 재사용하게 만든다.
		mockMvc.perform(signUpRequest(EMAIL, "1234567"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("정확히 8자인 비밀번호는 통과한다")
	void signUpWithExactlyEightCharacterPasswordIsAccepted() throws Exception {
		// 경계에서 한 글자 차이로 갈리는 실수를 막는다.
		mockMvc.perform(signUpRequest(EMAIL, "12345678"))
			.andExpect(status().isCreated());
	}

	// ------------------------------------------------------------------
	// 로그인
	// ------------------------------------------------------------------

	@Test
	@DisplayName("로그인하면 200 이고 accessUuid 를 받는다")
	void logInReturnsAccessUuid() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD)).andExpect(status().isCreated());

		mockMvc.perform(logInRequest(EMAIL, PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessUuid").isNotEmpty())
			.andExpect(jsonPath("$.data.user.email").value(EMAIL));
	}

	@Test
	@DisplayName("로그인할 때마다 accessUuid 가 새 값으로 바뀌고 이전 값은 통하지 않는다")
	void logInIssuesNewAccessUuidAndInvalidatesPreviousOne() throws Exception {
		String firstUuid = accessUuidOf(mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated()));

		String secondUuid = accessUuidOf(mockMvc.perform(logInRequest(EMAIL, PASSWORD))
			.andExpect(status().isOk()));

		// 덮어쓰기 때문에 이전 값이 자동으로 무효가 된다. 폐기 목록을 따로
		// 관리할 필요가 없고, 그래서 한 계정은 항상 한 기기에서만 로그인 상태다.
		assertThat(secondUuid).isNotEqualTo(firstUuid);

		mockMvc.perform(get(PROTECTED_APP_PATH).header(APP_HEADER, firstUuid))
			.andExpect(status().isUnauthorized());

		// 새 값은 통과한다. 여기서 확인하는 것은 "옛 값이 막히고 새 값이
		// 통한다" 는 대비이지 응답 내용이 아니다.
		mockMvc.perform(get(PROTECTED_APP_PATH).header(APP_HEADER, secondUuid))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("없는 이메일과 틀린 비밀번호는 똑같은 응답을 받는다")
	void missingEmailAndWrongPasswordAreIndistinguishable() throws Exception {
		mockMvc.perform(signUpRequest(EMAIL, PASSWORD)).andExpect(status().isCreated());

		// 두 경우를 구분해 알려주면 "이 이메일은 가입되어 있다" 는 사실이 새어 나간다.
		// 공격자가 이메일 목록을 넣어보며 가입 여부를 알아낼 수 있게 된다.
		mockMvc.perform(logInRequest("nobody@example.com", PASSWORD))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));

		mockMvc.perform(logInRequest(EMAIL, "wrongPassword"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	// ------------------------------------------------------------------
	// 로그아웃
	// ------------------------------------------------------------------

	@Test
	@DisplayName("로그아웃하면 204 이고 그 뒤로는 같은 값이 통하지 않는다")
	void logOutInvalidatesAccessUuid() throws Exception {
		String accessUuid = accessUuidOf(mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated()));

		mockMvc.perform(post("/api/v1/auth/logout").header(APP_HEADER, accessUuid))
			.andExpect(status().isNoContent());

		// 만료가 없는 구조라 로그아웃이 값을 무효로 만드는 유일한 수단이다.
		mockMvc.perform(get(PROTECTED_APP_PATH).header(APP_HEADER, accessUuid))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("로그아웃하려면 인증이 필요하다")
	void logOutRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------
	// 인증 필터
	// ------------------------------------------------------------------

	@Test
	@DisplayName("헤더 없이 보호된 앱 경로를 호출하면 401 이고 공통 error 형태다")
	void protectedPathWithoutHeaderReturnsUnauthorized() throws Exception {
		mockMvc.perform(get(PROTECTED_APP_PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@ParameterizedTest
	@DisplayName("엉뚱한 값을 헤더에 넣으면 401 이다")
	@ValueSource(strings = {"이건-uuid-가-아니다", "00000000-0000-0000-0000-000000000000"})
	void protectedPathWithUnknownHeaderValueReturnsUnauthorized(String value) throws Exception {
		// 형식이 깨진 값과 형식은 맞지만 DB 에 없는 값을 함께 확인한다.
		// 앞의 것은 파싱에서, 뒤의 것은 조회에서 걸러져야 한다.
		mockMvc.perform(get(PROTECTED_APP_PATH).header(APP_HEADER, value))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("앱 헤더로는 기기 API 를 호출할 수 없다")
	void appHeaderCannotAccessDeviceApi() throws Exception {
		String accessUuid = accessUuidOf(mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated()));

		// 앱용 필터가 기기 경로에 걸리면 기기 요청이 USERS 에서 유저를 찾다가
		// 반드시 실패한다. 체인이 정말 나뉘어 있는지를 확인하는 대목이다.
		mockMvc.perform(post(PROTECTED_DEVICE_PATH).header(APP_HEADER, accessUuid))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("기기 헤더로는 앱 API 를 호출할 수 없다")
	void deviceHeaderCannotAccessAppApi() throws Exception {
		String deviceAccessUuid = insertPairedDevice();

		mockMvc.perform(get(PROTECTED_APP_PATH).header(DEVICE_HEADER, deviceAccessUuid))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("기기 헤더로 기기 API 를 호출하면 통과한다")
	void deviceHeaderPassesDeviceApi() throws Exception {
		String deviceAccessUuid = insertPairedDevice();

		// 여기서 확인하는 것은 "필터를 통과했는가" 하나다. sync 가 실제로 무엇을
		// 하는지는 DeviceSyncIntegrationTest 가 본다. 그래서 본문은 최소한만 보낸다.
		mockMvc.perform(post(PROTECTED_DEVICE_PATH)
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 50, "firmware": "1.0.0", "completions": [], "dates": []}"""))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("연결 해제된 기기의 값은 통하지 않는다")
	void releasedDeviceCannotAuthenticate() throws Exception {
		String deviceAccessUuid = insertPairedDevice();

		// soft delete 된 행을 조회에서 거르지 않으면, 연결을 해제한 기기가
		// 계속 자녀의 루틴을 받아 갈 수 있다.
		jdbcTemplate.update(
			"update devices set deleted_at = now() where device_access_uuid = cast(? as uuid)",
			deviceAccessUuid);

		mockMvc.perform(post(PROTECTED_DEVICE_PATH).header(DEVICE_HEADER, deviceAccessUuid))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("탈퇴한 계정의 값은 통하지 않는다")
	void withdrawnUserCannotAuthenticate() throws Exception {
		String accessUuid = accessUuidOf(mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated()));

		jdbcTemplate.update("update users set deleted_at = now() where email = ?", EMAIL);

		mockMvc.perform(get(PROTECTED_APP_PATH).header(APP_HEADER, accessUuid))
			.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------
	// 공통 응답 계층 — 존재하지 않는 경로 · 허용되지 않은 메서드
	// ------------------------------------------------------------------

	@Test
	@DisplayName("인증한 뒤 없는 경로를 부르면 404 이고 공통 error 형태다")
	void unknownPathReturnsNotFoundInCommonFormat() throws Exception {
		// "API.md" 3-1 이 NOT_FOUND 를 정의해 두었다. 이 처리가 없으면
		// 존재하지 않는 경로가 500 으로 나가고, 앱은 "서버가 고장났다" 로 읽는다.
		// 오타 하나 때문에 장애 신고가 들어오게 된다.
		String accessUuid = accessUuidOf(mockMvc.perform(signUpRequest(EMAIL, PASSWORD))
			.andExpect(status().isCreated()));

		mockMvc.perform(post("/api/v1/기억에없는경로").header(APP_HEADER, accessUuid))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("인증하지 않고 없는 경로를 부르면 404 가 아니라 401 이다")
	void unknownPathWithoutAuthenticationReturnsUnauthorized() throws Exception {
		// 인증도 하지 않은 호출자에게 "그 경로는 없다" 와 "그 경로는 있는데
		// 권한이 없다" 를 구분해 알려주면, 어떤 경로가 존재하는지가 새어 나간다.
		// 보안 필터가 컨트롤러보다 먼저 도는 덕분에 이 구분이 자연히 막힌다.
		mockMvc.perform(post("/api/v1/기억에없는경로"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("허용되지 않은 메서드로 부르면 405 다")
	void wrongHttpMethodReturnsMethodNotAllowed() throws Exception {
		// login 은 POST 전용이다. "API.md" 3-1 의 METHOD_NOT_ALLOWED.
		mockMvc.perform(get("/api/v1/auth/login"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private org.springframework.test.web.servlet.RequestBuilder signUpRequest(
		String email, String password) {

		return post("/api/v1/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

	private org.springframework.test.web.servlet.RequestBuilder logInRequest(
		String email, String password) {

		return post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

	private String accessUuidOf(org.springframework.test.web.servlet.ResultActions resultActions)
		throws Exception {

		String body = resultActions.andReturn().getResponse().getContentAsString();
		JsonNode root = objectMapper.readTree(body);
		return root.path("data").path("accessUuid").asText();
	}

	/**
	 * 페어링이 끝난 기기 한 대를 만들고 그 device_access_uuid 를 돌려준다.
	 *
	 * 페어링 API(2-2)가 아직 없어 DB 에 직접 넣는다. 여기서 확인하려는 것은
	 * 페어링 절차가 아니라 "그 값으로 인증이 되는가" 이므로 이렇게 해도 된다.
	 */
	private String insertPairedDevice() {
		Long userId = jdbcTemplate.queryForObject(
			"insert into users (email, password_hash) values (?, ?) returning id",
			Long.class, "device-owner@example.com", "hashed");

		Long childId = jdbcTemplate.queryForObject(
			"insert into children (user_id, name) values (?, ?) returning id",
			Long.class, userId, "아이");

		return jdbcTemplate.queryForObject(
			"insert into devices (child_id, device_uid, status, device_access_uuid)"
				+ " values (?, ?, 'ACTIVE', gen_random_uuid())"
				+ " returning cast(device_access_uuid as varchar)",
			String.class, childId, "device-uid-9001");
	}

}
