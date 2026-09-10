package artistic.software.khu.artistic_software_khu.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;

/**
 * 형식이 깨진 요청을 서버가 어떻게 다루는지 확인한다. "API.md" 2-3.
 *
 * 문서는 "입력값 검증 실패, 잘못된 파라미터" 를 400 으로 정해 두었다. 그런데
 * 경로 변수의 타입이 맞지 않거나 본문이 JSON 이 아니면 스프링이 던지는 예외가
 * 우리 처리기의 "나머지 전부" 갈래로 떨어져 500 이 나간다.
 *
 * 500 이 나가면 두 가지가 한꺼번에 잘못된다. 앱은 "서버가 고장났다" 로 보고
 * 재시도하는데 몇 번을 보내도 같은 결과이고, 실제로는 앱이 보낸 값이 틀린
 * 것이라 서버 쪽에는 고칠 것이 없다. 원인을 찾는 동안 장애로 취급된다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvalidRequestIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String PASSWORD = "password1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String accessUuid;

	private long childId;

	@BeforeEach
	void prepareOwnerAndChild() throws Exception {
		accessUuid = signUp("invalid-request-owner@example.com");
		childId = registerChild(accessUuid);
	}

	@Test
	@DisplayName("경로 변수가 숫자가 아니면 400 이다")
	void nonNumericPathVariableIsBadRequest() throws Exception {
		// 앱이 childId 자리에 빈 값이나 "undefined" 를 넣어 보내는 일은
		// 실제로 자주 일어난다. 서버 잘못이 아니므로 500 이면 안 된다.
		mockMvc.perform(get("/api/v1/children/undefined").header(APP_HEADER, accessUuid))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("날짜 형식이 깨진 조회 파라미터는 400 이다")
	void malformedDateParameterIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, accessUuid)
				.param("from", "2026-13-45")
				.param("to", "2026-09-30"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("필수 조회 파라미터가 빠지면 400 이다")
	void missingRequiredParameterIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, accessUuid))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("본문이 JSON 이 아니면 400 이다")
	void malformedJsonBodyIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("필드 타입이 맞지 않으면 400 이다")
	void wrongFieldTypeIsBadRequest() throws Exception {
		// birthDate 자리에 날짜가 아닌 값이 오는 경우다.
		mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김아이", "birthDate": "어제", "relationship": "PARENT"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("비밀번호 없이 로그인하면 400 이다")
	void logInWithoutPasswordIsBadRequest() throws Exception {
		// 이메일은 실제로 가입된 것이어야 한다. 없는 이메일이면 비밀번호를
		// 확인하기 전에 걸러져 이 경우를 지나쳐 버린다.
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "invalid-request-owner@example.com"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("순서 변경 요청에 목록이 없으면 400 이다")
	void reorderWithoutListIsBadRequest() throws Exception {
		long bigRoutineId = createBigRoutine();

		// "API.md" 9장이 "누락이 있으면 ROUTINE_ORDER_MISMATCH" 라고 정했다.
		// 통째로 빠진 것도 누락이다.
		mockMvc.perform(put("/api/v1/big-routines/" + bigRoutineId + "/small-routines/order")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("[]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_ORDER_MISMATCH"));
	}

	@Test
	@DisplayName("제목 없이 양식을 저장하면 400 이다")
	void routineTemplateWithoutTitleIsBadRequest() throws Exception {
		// 제목은 "ROUTINE_TEMPLATES.title" 이 not null 이라 DB 까지 내려가면
		// 제약 위반으로 500 이 된다. 그 전에 400 으로 돌려줘야 앱이 무엇을
		// 고쳐야 하는지 알 수 있다.
		mockMvc.perform(post("/api/v1/children/" + childId + "/routine-templates")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"startTime": "19:00", "endTime": "20:00", "smallRoutines": []}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("별칭 없이 기기 정보를 수정하면 400 이다")
	void deviceUpdateWithoutNicknameIsBadRequest() throws Exception {
		long deviceId = startPairing();

		// "API.md" 7장이 nickname 을 필수로 두었다. 지금은 빈 요청이 200 으로
		// 돌아가 앱이 "바뀌었다" 고 믿게 되는데 실제로는 아무것도 바뀌지 않는다.
		mockMvc.perform(patch("/api/v1/devices/" + deviceId)
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("수정으로 종료 시각을 시작 시각보다 앞당길 수 없다")
	void updatingBigRoutineCannotInvertTimeRange() throws Exception {
		long bigRoutineId = createBigRoutine();

		// 만들 때는 "endTime 은 startTime 보다 뒤" 를 검사한다("API.md" 9장).
		// 고칠 때 검사하지 않으면 같은 규칙이 수정 한 번으로 깨진다.
		mockMvc.perform(patch("/api/v1/big-routines/" + bigRoutineId)
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"endTime": "07:00"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_TIME_RANGE"));
	}

	@Test
	@DisplayName("기기 상태 조회 응답에 childId 가 들어 있다")
	void deviceDetailContainsChildId() throws Exception {
		long deviceId = startPairing();

		// "API.md" 7장 "GET /devices/:deviceId" 응답에 childId 가 있다.
		// 앱은 기기 하나를 열었을 때 어느 자녀의 것인지 이 값으로 안다.
		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, accessUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.childId").value(childId));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private String signUp(String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("accessUuid").asText();
	}

	private long registerChild(String ownerUuid) throws Exception {
		String body = mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김아이", "birthDate": "2018-03-02", "relationship": "PARENT"}"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("childId").asLong();
	}

	private long createBigRoutine() throws Exception {
		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "아침 준비",
					  "startTime": "07:30",
					  "endTime": "08:30",
					  "repeatType": "RANGE",
					  "startDate": "2099-01-05",
					  "endDate": "2099-01-05",
					  "smallRoutines": [{"title": "세수하기"}]
					}"""))
			.andExpect(status().isCreated());

		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, accessUuid)
				.param("from", "2099-01-05")
				.param("to", "2099-01-05"))
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body)
			.path("data").get(0).path("bigRoutines").get(0).path("bigRoutineId").asLong();
	}

	private long startPairing() throws Exception {
		String body = mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("deviceId").asLong();
	}

}
