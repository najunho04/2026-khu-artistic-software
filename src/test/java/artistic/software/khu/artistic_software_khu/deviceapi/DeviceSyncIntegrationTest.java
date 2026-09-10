package artistic.software.khu.artistic_software_khu.deviceapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기기 동기화. "API.md" 8장 POST /device-api/v1/sync.
 *
 * **이 엔드포인트가 아이에게 루틴이 닿는 유일한 경로다.** 앞의 모든 구간이
 * "앱이 데이터를 넣는" 쪽이었고, 여기서 처음으로 그 데이터가 기기로 나간다.
 *
 * push 와 pull 을 한 번에 한다. 기기가 완료 기록과 배터리를 올리고, 같은 응답에
 * 하루치 루틴을 받아 간다. 호출을 두 번으로 나누면 기기가 그 사이에 꺼졌을 때
 * 올린 것은 반영됐는데 받은 것은 없는 어중간한 상태가 된다.
 *
 * 가장 중요한 성질은 **멱등성**이다. 기기는 네트워크가 끊기면 같은 요청을 다시
 * 보내는 것 외에 할 수 있는 일이 없다. 두 번 보냈다고 완료가 두 번 쌓이거나
 * 오류가 나면 기기 쪽에서 손쓸 방법이 없다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceSyncIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String PASSWORD = "password1234";

	// 오늘에 가까운 날짜를 쓰면 테스트를 언제 돌리느냐에 따라 결과가 달라진다.
	private static final String ROUTINE_DATE = "2099-01-05";

	private String ownerUuid;

	private long childId;

	private String deviceAccessUuid;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@PersistenceContext
	private EntityManager entityManager;

	@BeforeEach
	void prepareOnboardedDevice() throws Exception {
		ownerUuid = signUp("sync-owner@example.com");
		childId = registerChild(ownerUuid);
		deviceAccessUuid = pairAndClaim("device-uid-sync-1");
	}

	// ------------------------------------------------------------------
	// pull — 루틴 받아 가기
	// ------------------------------------------------------------------

	@Test
	@DisplayName("기기가 하루치 루틴을 받아 간다")
	void deviceReceivesRoutinesForRequestedDates() throws Exception {
		createRoutine("아침 준비", "세수하기", "양치하기");

		mockMvc.perform(syncRequest("""
			{"battery": 78, "firmware": "1.0.3", "completions": [], "dates": ["%s"]}"""
			.formatted(ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.serverTime").isNotEmpty())
			.andExpect(jsonPath("$.data.accepted").value(0))
			.andExpect(jsonPath("$.data.routines.length()").value(1))
			.andExpect(jsonPath("$.data.routines[0].date").value(ROUTINE_DATE))
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].title").value("아침 준비"))
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].smallRoutines.length()").value(2));
	}

	@Test
	@DisplayName("루틴이 없는 날짜를 요청해도 루틴을 만들지 않는다")
	void syncNeverCreatesRoutines() throws Exception {
		mockMvc.perform(syncRequest("""
			{"battery": 50, "firmware": "1.0.3", "completions": [], "dates": ["2099-02-02"]}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.routines.length()").value(0));

		// 기기에서는 루틴 생성이 불가능하다. sync 가 없는 날짜를 만들어내기
		// 시작하면 앱이 만들지 않은 루틴이 아이 기기에 뜨게 된다.
		Integer routineCount = jdbcTemplate.queryForObject(
			"select count(*) from big_routines where child_id = ?", Integer.class, childId);

		assertThat(routineCount).isZero();
	}

	@Test
	@DisplayName("다른 아이의 루틴은 받아 갈 수 없다")
	void deviceOnlyReceivesItsOwnChildRoutines() throws Exception {
		createRoutine("아침 준비", "세수하기");

		// 다른 보호자와 자녀를 만들고 그 아이에게도 루틴을 넣는다.
		String otherUuid = signUp("sync-other@example.com");
		long otherChildId = registerChild(otherUuid);
		createRoutineFor(otherUuid, otherChildId, "남의 루틴", "남의 할일");

		String body = mockMvc.perform(syncRequest("""
			{"battery": 78, "firmware": "1.0.3", "completions": [], "dates": ["%s"]}"""
			.formatted(ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		// 기기는 자기가 붙은 자녀의 것만 받아야 한다. 이 조건이 빠지면
		// device_access_uuid 하나로 다른 아이의 하루가 전부 새어 나간다.
		assertThat(body).doesNotContain("남의 루틴").doesNotContain("남의 할일");
	}

	// ------------------------------------------------------------------
	// push — 완료 기록 올리기
	// ------------------------------------------------------------------

	@Test
	@DisplayName("완료 기록을 올리면 상태가 DONE 으로 바뀐다")
	void completionsAreApplied() throws Exception {
		createRoutine("아침 준비", "세수하기", "양치하기");

		long smallRoutineId = firstSmallRoutineId();

		mockMvc.perform(syncRequest("""
			{
			  "battery": 78, "firmware": "1.0.3",
			  "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-01-05T07:42:00Z"}
			  ],
			  "dates": ["%s"]
			}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accepted").value(1))
			// 같은 응답의 pull 부분에도 바뀐 상태가 담겨야 한다. push 를 pull 보다
			// 뒤에 두면 방금 올린 완료가 응답에 빠진다.
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].smallRoutines[0].status")
				.value("DONE"));
	}

	@Test
	@DisplayName("같은 완료 기록을 두 번 보내도 결과가 같다")
	void repeatedCompletionsAreIdempotent() throws Exception {
		createRoutine("아침 준비", "세수하기");

		long smallRoutineId = firstSmallRoutineId();
		String request = """
			{
			  "battery": 78, "firmware": "1.0.3",
			  "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-01-05T07:42:00Z"}
			  ],
			  "dates": ["%s"]
			}""".formatted(smallRoutineId, ROUTINE_DATE);

		mockMvc.perform(syncRequest(request)).andExpect(status().isOk());

		// 기기는 네트워크가 끊기면 같은 요청을 다시 보내는 것 외에 할 수 있는
		// 일이 없다. 두 번 보냈다고 오류가 나면 기기 쪽에서 손쓸 방법이 없다.
		mockMvc.perform(syncRequest(request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accepted").value(1));

		// UPDATE 기반이라 행이 늘지 않는다.
		Integer doneCount = jdbcTemplate.queryForObject(
			"select count(*) from small_routines where id = ? and status = 'DONE'",
			Integer.class, smallRoutineId);

		assertThat(doneCount).isEqualTo(1);
	}

	@Test
	@DisplayName("삭제된 할 일이 섞여 있어도 나머지는 반영된다")
	void deletedSmallRoutineIsSkippedInsteadOfFailingAll() throws Exception {
		createRoutine("아침 준비", "세수하기", "양치하기");

		long liveId = firstSmallRoutineId();

		// 기기가 오프라인인 동안 보호자가 할 일 하나를 지운 상황이다.
		// 흔하게 일어나며, 기기는 그 사실을 알 방법이 없다.
		mockMvc.perform(syncRequest("""
			{
			  "battery": 78, "firmware": "1.0.3",
			  "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-01-05T07:42:00Z"},
			    {"smallRoutineId": 99999999, "status": "DONE", "completedAt": "2099-01-05T07:43:00Z"}
			  ],
			  "dates": ["%s"]
			}""".formatted(liveId, ROUTINE_DATE)))
			.andExpect(status().isOk())
			// 전체를 실패시키면 기기는 재시도밖에 할 수 없고, 다시 보내도
			// 똑같이 실패해 그 뒤의 완료가 영원히 올라가지 못한다.
			.andExpect(jsonPath("$.data.accepted").value(1));
	}

	@Test
	@DisplayName("배터리와 펌웨어와 마지막 동기화 시각이 갱신된다")
	void batteryFirmwareAndLastSyncedAtAreUpdated() throws Exception {
		mockMvc.perform(syncRequest("""
			{"battery": 42, "firmware": "2.0.0", "completions": [], "dates": []}"""))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();

		var row = jdbcTemplate.queryForMap(
			"select battery_level, firmware_version, last_synced_at from devices"
				+ " where device_access_uuid = cast(? as uuid)", deviceAccessUuid);

		assertThat(row.get("battery_level")).isEqualTo(42);
		assertThat(row.get("firmware_version")).isEqualTo("2.0.0");
		// 앱의 기기 목록 화면이 "마지막으로 언제 연결됐나" 를 보여주는 근거다.
		assertThat(row.get("last_synced_at")).isNotNull();
	}

	// ------------------------------------------------------------------
	// 거절해야 하는 요청
	// ------------------------------------------------------------------

	@Test
	@DisplayName("헤더 없이 부르면 401 이고 기기용 코드가 나간다")
	void syncWithoutHeaderIsUnauthorized() throws Exception {
		mockMvc.perform(post("/device-api/v1/sync")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 78, "firmware": "1.0.3", "completions": [], "dates": []}"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("DEVICE_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("연결 해제된 기기는 sync 할 수 없다")
	void releasedDeviceCannotSync() throws Exception {
		long deviceId = jdbcTemplate.queryForObject(
			"select id from devices where device_access_uuid = cast(? as uuid)",
			Long.class, deviceAccessUuid);

		jdbcTemplate.update("update devices set deleted_at = now() where id = ?", deviceId);

		mockMvc.perform(syncRequest("""
			{"battery": 78, "firmware": "1.0.3", "completions": [], "dates": []}"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("날짜를 너무 많이 요청하면 거절한다")
	void tooManyDatesAreRejected() throws Exception {
		// 상한이 없으면 기기가 1년치를 한 번에 요청할 수 있고, 응답이 기기의
		// 메모리를 넘기면 그 기기는 그 뒤로 아무것도 받지 못한다.
		// 상한값 자체는 미확정이라 설정값으로 주입한다("API.md" 15장 #8).
		StringBuilder dates = new StringBuilder();

		for (int day = 1; day <= 10; day++) {
			dates.append("\"2099-01-%02d\"".formatted(day));
			if (day < 10) {
				dates.append(", ");
			}
		}

		mockMvc.perform(syncRequest("""
			{"battery": 78, "firmware": "1.0.3", "completions": [], "dates": [%s]}"""
			.formatted(dates)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("배터리 값이 범위를 벗어나면 거절한다")
	void batteryOutOfRangeIsRejected() throws Exception {
		mockMvc.perform(syncRequest("""
			{"battery": 120, "firmware": "1.0.3", "completions": [], "dates": []}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	// ------------------------------------------------------------------
	// 알 수 없는 완료 상태
	// ------------------------------------------------------------------

	@Test
	@DisplayName("status 가 DONE · PENDING 이 아니면 400 이고 아무것도 바뀌지 않는다")
	void unknownCompletionStatusIsRejected() throws Exception {
		createRoutine("아침 준비", "세수하기");

		long smallRoutineId = firstSmallRoutineId();

		// 먼저 정상적으로 완료로 만들어 둔다.
		mockMvc.perform(syncRequest("""
			{"battery": 70, "firmware": "1.0.3", "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-01-05T07:42:00Z"}
			], "dates": ["%s"]}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isOk());

		// 그 뒤 오타가 섞인 요청이 오면 거절한다. 거절하지 않으면 "DONE 이
		// 아닌 값" 이 전부 PENDING 으로 처리되어, 오타 하나로 아이가 한 일이
		// 조용히 지워진다.
		mockMvc.perform(syncRequest("""
			{"battery": 70, "firmware": "1.0.3", "completions": [
			    {"smallRoutineId": %d, "status": "BANANA", "completedAt": "2099-01-05T07:42:00Z"}
			], "dates": ["%s"]}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		entityManager.flush();
		entityManager.clear();

		Integer doneCount = jdbcTemplate.queryForObject(
			"select count(*) from small_routines where id = ? and status = 'DONE'",
			Integer.class, smallRoutineId);

		assertThat(doneCount).isOne();
	}

	@Test
	@DisplayName("status 가 없으면 400 이다")
	void missingCompletionStatusIsRejected() throws Exception {
		createRoutine("아침 준비", "세수하기");

		mockMvc.perform(syncRequest("""
			{"battery": 70, "firmware": "1.0.3", "completions": [
			    {"smallRoutineId": %d, "completedAt": "2099-01-05T07:42:00Z"}
			], "dates": ["%s"]}""".formatted(firstSmallRoutineId(), ROUTINE_DATE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("PENDING 은 정상 값이라 완료를 되돌린다")
	void pendingStatusStillUndoesCompletion() throws Exception {
		createRoutine("아침 준비", "세수하기");

		long smallRoutineId = firstSmallRoutineId();

		mockMvc.perform(syncRequest("""
			{"battery": 70, "firmware": "1.0.3", "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-01-05T07:42:00Z"}
			], "dates": ["%s"]}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isOk());

		// 아이가 실수로 눌렀다가 취소하는 일은 실제로 일어난다.
		mockMvc.perform(syncRequest("""
			{"battery": 70, "firmware": "1.0.3", "completions": [
			    {"smallRoutineId": %d, "status": "PENDING", "completedAt": null}
			], "dates": ["%s"]}""".formatted(smallRoutineId, ROUTINE_DATE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.routines[0].bigRoutines[0].smallRoutines[0].status")
				.value("PENDING"));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private RequestBuilder syncRequest(String body) {
		return post("/device-api/v1/sync")
			.header(DEVICE_HEADER, deviceAccessUuid)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private String signUp(String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("accessUuid").asText();
	}

	private long registerChild(String accessUuid) throws Exception {
		String body = mockMvc.perform(post("/api/v1/children")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김아이", "birthDate": "2018-03-02", "relationship": "PARENT"}"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("childId").asLong();
	}

	/** 페어링부터 claim 까지 거쳐 실제 기기 신분증을 받아 온다. */
	private String pairAndClaim(String deviceUid) throws Exception {
		String pairingBody = mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		String pairingCode =
			objectMapper.readTree(pairingBody).path("data").path("pairingCode").asText();

		String claimBody = mockMvc.perform(post("/device-api/v1/claim")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"pairingCode": "%s", "deviceUid": "%s", "firmware": "1.0.0"}"""
					.formatted(pairingCode, deviceUid)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(claimBody).path("data").path("deviceAccessUuid").asText();
	}

	private void createRoutine(String title, String... smallRoutineTitles) throws Exception {
		createRoutineFor(ownerUuid, childId, title, smallRoutineTitles);
	}

	private void createRoutineFor(
		String accessUuid, long targetChildId, String title, String... smallRoutineTitles)
		throws Exception {

		StringBuilder smallRoutines = new StringBuilder();

		for (int index = 0; index < smallRoutineTitles.length; index++) {
			smallRoutines.append("{\"title\": \"%s\"}".formatted(smallRoutineTitles[index]));
			if (index < smallRoutineTitles.length - 1) {
				smallRoutines.append(", ");
			}
		}

		mockMvc.perform(post("/api/v1/children/" + targetChildId + "/big-routines")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "%s",
					  "startTime": "07:30",
					  "endTime": "08:30",
					  "repeatType": "RANGE",
					  "startDate": "%s",
					  "endDate": "%s",
					  "smallRoutines": [%s]
					}""".formatted(title, ROUTINE_DATE, ROUTINE_DATE, smallRoutines)))
			.andExpect(status().isCreated());
	}

	private long firstSmallRoutineId() throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", ROUTINE_DATE)
				.param("to", ROUTINE_DATE))
			.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		return root.path("data").get(0).path("bigRoutines").get(0)
			.path("smallRoutines").get(0).path("smallRoutineId").asLong();
	}

}
