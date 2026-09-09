package artistic.software.khu.artistic_software_khu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 온보딩 전 구간. "ROADMAP.md" 2-3 마일스톤이고 "API.md" 14장의 0~5단계다.
 *
 * **조각마다 테스트가 있는데도 이 테스트가 따로 필요한 이유**는, 조각 테스트가
 * 저마다 "앞 단계는 됐다고 치고" 시작하기 때문이다. 루틴 테스트는 자녀를 직접
 * 만들어 쓰고, sync 테스트는 페어링을 직접 해서 쓴다. 그렇게 하면 각 구간은
 * 통과하는데 **구간과 구간을 잇는 값이 어긋나는 것은 아무도 보지 못한다.**
 *
 * 여기서는 한 단계의 응답을 다음 단계의 입력으로 그대로 넘긴다. 중간에 DB 를
 * 건드리거나 값을 지어내지 않는다. 그래야 실제 앱과 기기가 하는 것과 같아진다.
 *
 * 흐름의 특징은 **두 방향이 만난다**는 점이다. 앱이 서버에 코드를 요청하고,
 * 그 코드를 핫스팟으로 기기에 넘기고(단방향이라 앱은 성공 여부를 모른다),
 * 기기가 그 코드로 서버에 붙는다. 앱은 서버를 폴링해서야 붙었음을 안다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingE2EIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String ROUTINE_DATE = "2099-01-05";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("가입부터 기기가 루틴을 받아 가기까지 전 구간이 이어진다")
	void onboardingFlowConnectsEndToEnd() throws Exception {
		// ── 0단계. 회원가입 → 성명 입력 ──────────────────────────────
		String signUpBody = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "onboarding@example.com", "password": "password1234"}"""))
			.andExpect(status().isCreated())
			// 이름이 비어 있는 것이 앱에게 "성명 입력 화면으로 가라" 는 신호다.
			.andExpect(jsonPath("$.data.user.name").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		String accessUuid = read(signUpBody, "data", "accessUuid");

		mockMvc.perform(patch("/api/v1/users/me")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김보호"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("김보호"));

		// ── 1단계. 자녀 등록 → 페어링 코드 발급 ─────────────────────
		String childBody = mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김아이", "birthDate": "2018-03-02", "relationship": "PARENT"}"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		long childId = Long.parseLong(read(childBody, "data", "childId"));

		String pairingBody = mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "거실 예소"}""".formatted(childId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andReturn().getResponse().getContentAsString();

		String pairingCode = read(pairingBody, "data", "pairingCode");
		long deviceId = Long.parseLong(read(pairingBody, "data", "deviceId"));

		// 2단계(앱 → 기기 핫스팟 전달)는 서버를 거치지 않는다. 코드가 기기 손에
		// 들어갔다는 것만 아래 claim 이 대신 보여준다.

		// ── 폴링. 아직 기기가 안 붙었으므로 PENDING ─────────────────
		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, accessUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PENDING"));

		// ── 3단계. 기기가 코드로 claim ──────────────────────────────
		String claimBody = mockMvc.perform(post("/device-api/v1/claim")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"pairingCode": "%s", "deviceUid": "esp32-abc-001", "firmware": "1.0.0"}"""
					.formatted(pairingCode)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		String deviceAccessUuid = read(claimBody, "data", "deviceAccessUuid");

		// ── 4단계. 다시 폴링. 이제 ACTIVE ───────────────────────────
		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, accessUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.nickname").value("거실 예소"));

		// ── 보호자가 루틴을 만든다 ──────────────────────────────────
		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "아침 준비",
					  "startTime": "07:30",
					  "endTime": "08:30",
					  "repeatType": "RANGE",
					  "startDate": "%s",
					  "endDate": "%s",
					  "smallRoutines": [{"title": "세수하기"}, {"title": "양치하기"}]
					}""".formatted(ROUTINE_DATE, ROUTINE_DATE)))
			.andExpect(status().isCreated());

		// ── 5단계. 기기가 sync 로 루틴을 받아 간다 ──────────────────
		String syncBody = mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 87, "firmware": "1.0.0", "completions": [], "dates": ["%s"]}"""
					.formatted(ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].title").value("아침 준비"))
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].smallRoutines.length()")
				.value(2))
			.andReturn().getResponse().getContentAsString();

		// ── 아이가 할 일을 끝내고 기기가 올린다 ─────────────────────
		JsonNode firstSmallRoutine = objectMapper.readTree(syncBody)
			.path("data").path("routines").get(0)
			.path("bigRoutines").get(0)
			.path("smallRoutines").get(0);

		long smallRoutineId = firstSmallRoutine.path("smallRoutineId").asLong();

		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "battery": 85, "firmware": "1.0.0",
					  "completions": [
					    {"smallRoutineId": %d, "status": "DONE",
					     "completedAt": "2099-01-05T07:42:00Z"}
					  ],
					  "dates": ["%s"]
					}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accepted").value(1));

		// ── 보호자가 앱에서 그 결과를 본다 ──────────────────────────
		// 여기가 이 테스트의 끝이자 제품의 목적이다. 아이가 기기에서 한 일이
		// 보호자의 캘린더에 나타나는 것. 중간의 어느 연결부가 어긋나도
		// 이 마지막 단언이 실패한다.
		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, accessUuid)
				.param("from", ROUTINE_DATE)
				.param("to", ROUTINE_DATE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].totalCount").value(2))
			.andExpect(jsonPath("$.data[0].doneCount").value(1))
			.andExpect(jsonPath("$.data[0].completionRate").value(50.0));
	}

	@Test
	@DisplayName("기기가 붙기 전에는 그 자녀의 루틴을 아무도 받아 갈 수 없다")
	void routinesAreUnreachableBeforePairing() throws Exception {
		String accessUuid = signUpAndNameGuardian("before-pairing@example.com");
		long childId = registerChild(accessUuid);

		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "아침 준비", "startTime": "07:30", "endTime": "08:30",
					  "repeatType": "RANGE", "startDate": "%s", "endDate": "%s",
					  "smallRoutines": [{"title": "세수하기"}]
					}""".formatted(ROUTINE_DATE, ROUTINE_DATE)))
			.andExpect(status().isCreated());

		// 아직 claim 한 기기가 없으므로 기기용 신분증 자체가 존재하지 않는다.
		// 지어낸 값으로는 통과할 수 없다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, "00000000-0000-0000-0000-000000000000")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 50, "firmware": "1.0.0", "completions": [], "dates": ["%s"]}"""
					.formatted(ROUTINE_DATE)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("한 자녀에게 기기 두 대를 붙이면 둘 다 같은 루틴을 받는다")
	void twoDevicesOnOneChildReceiveSameRoutines() throws Exception {
		String accessUuid = signUpAndNameGuardian("two-devices@example.com");
		long childId = registerChild(accessUuid);

		String firstDeviceUuid = pairAndClaim(accessUuid, childId, "esp32-first");
		String secondDeviceUuid = pairAndClaim(accessUuid, childId, "esp32-second");

		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "아침 준비", "startTime": "07:30", "endTime": "08:30",
					  "repeatType": "RANGE", "startDate": "%s", "endDate": "%s",
					  "smallRoutines": [{"title": "세수하기"}]
					}""".formatted(ROUTINE_DATE, ROUTINE_DATE)))
			.andExpect(status().isCreated());

		// 자녀 1명당 기기 N대가 기획 변경으로 들어온 구조다. 방 두 곳에 하나씩
		// 두는 경우를 생각하면, 둘 다 같은 루틴을 보여줘야 한다.
		for (String deviceUuid : new String[] {firstDeviceUuid, secondDeviceUuid}) {
			mockMvc.perform(post("/device-api/v1/sync")
					.header(DEVICE_HEADER, deviceUuid)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"battery": 50, "firmware": "1.0.0", "completions": [], "dates": ["%s"]}"""
						.formatted(ROUTINE_DATE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].title").value("아침 준비"));
		}
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private String signUpAndNameGuardian(String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "%s", "password": "password1234"}""".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		String accessUuid = read(body, "data", "accessUuid");

		mockMvc.perform(patch("/api/v1/users/me")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김보호"}"""))
			.andExpect(status().isOk());

		return accessUuid;
	}

	private long registerChild(String accessUuid) throws Exception {
		String body = mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김아이", "birthDate": "2018-03-02", "relationship": "PARENT"}"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return Long.parseLong(read(body, "data", "childId"));
	}

	private String pairAndClaim(String accessUuid, long childId, String deviceUid)
		throws Exception {

		String pairingBody = mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		String claimBody = mockMvc.perform(post("/device-api/v1/claim")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"pairingCode": "%s", "deviceUid": "%s", "firmware": "1.0.0"}"""
					.formatted(read(pairingBody, "data", "pairingCode"), deviceUid)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return read(claimBody, "data", "deviceAccessUuid");
	}

	/**
	 * 응답 본문에서 값을 꺼낸다. 단계마다 같은 코드를 반복하지 않기 위한 것이다.
	 */
	private String read(String body, String... path) {
		JsonNode node = objectMapper.readTree(body);

		for (String field : path) {
			node = node.path(field);
		}

		assertThat(node.isMissingNode())
			.as("응답에 %s 가 없다", String.join(".", path))
			.isFalse();

		return node.asString();
	}

}
