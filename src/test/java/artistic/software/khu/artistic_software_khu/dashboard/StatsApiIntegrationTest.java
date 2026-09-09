package artistic.software.khu.artistic_software_khu.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 통계와 대시보드. "API.md" 11장.
 *
 * 여기서 확인하려는 것의 핵심은 **분모**다. 이행률은 n/N 인데, N 에 무엇을
 * 넣느냐에 따라 숫자가 통째로 달라지고 **틀려도 오류가 나지 않는다.**
 * 화면에 그럴듯한 숫자가 떠 있어서 아무도 이상하다고 생각하지 않는다.
 *
 * 그래서 지운 할 일, 기간 밖의 할 일, 할 일이 없는 기간을 각각 확인한다.
 *
 * 날짜는 오늘 기준으로 만든다. 통계가 "최근 N일" 이라 먼 미래 날짜를 쓰면
 * 아무 기간에도 걸리지 않기 때문이다. 루틴 테스트가 2099년을 쓴 것과 반대다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsApiIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String PASSWORD = "password1234";

	// 서버는 UTC 로 돌지만 통계 경계는 KST 기준이다("API.md" 11-1).
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final LocalDate today = LocalDate.now(KOREA_ZONE);

	private String ownerUuid;

	private String strangerUuid;

	private long childId;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void prepareGuardianAndChild() throws Exception {
		ownerUuid = signUp("stats-owner@example.com");
		strangerUuid = signUp("stats-stranger@example.com");
		childId = registerChild(ownerUuid);
	}

	// ------------------------------------------------------------------
	// 기간별 이행률
	// ------------------------------------------------------------------

	@Test
	@DisplayName("오늘 할 일 4개 중 2개를 하면 50% 다")
	void dayStatsCountsTodayOnly() throws Exception {
		createRoutineOn(today, "아침 준비", "할일1", "할일2", "할일3", "할일4");
		completeSmallRoutines(today, 2);

		mockMvc.perform(statsRequest("DAY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.period").value("DAY"))
			.andExpect(jsonPath("$.data.from").value(today.toString()))
			.andExpect(jsonPath("$.data.to").value(today.toString()))
			.andExpect(jsonPath("$.data.doneCount").value(2))
			.andExpect(jsonPath("$.data.totalCount").value(4))
			.andExpect(jsonPath("$.data.completionRate").value(50.0));
	}

	@Test
	@DisplayName("지운 할 일은 분모에서 빠진다")
	void deletedSmallRoutineIsExcludedFromDenominator() throws Exception {
		createRoutineOn(today, "아침 준비", "할일1", "할일2", "할일3", "할일4");
		completeSmallRoutines(today, 2);

		// 보호자가 안 하던 할 일 하나를 지운 상황이다.
		//
		// 분모에 넣으면 2/4 = 50%, 빼면 2/3 = 66.7% 다. 둘 다 말이 되지만
		// 하나를 골라야 하고, 여기서 틀리면 모든 통계 숫자가 조용히 틀린다.
		long lastId = smallRoutineIdsOn(today).get(3);

		mockMvc.perform(delete("/api/v1/small-routines/" + lastId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		mockMvc.perform(statsRequest("DAY"))
			.andExpect(jsonPath("$.data.totalCount").value(3))
			.andExpect(jsonPath("$.data.doneCount").value(2))
			.andExpect(jsonPath("$.data.completionRate").value(66.7));
	}

	@Test
	@DisplayName("할 일이 하나도 없으면 이행률은 0 이다")
	void emptyPeriodHasZeroRate() throws Exception {
		// 100 으로 두면 아무것도 안 한 기간이 만점으로 보여 통계가 부풀려진다.
		mockMvc.perform(statsRequest("DAY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalCount").value(0))
			.andExpect(jsonPath("$.data.doneCount").value(0))
			.andExpect(jsonPath("$.data.completionRate").value(0.0));
	}

	@Test
	@DisplayName("기간 밖의 할 일은 세지 않는다")
	void routinesOutsidePeriodAreNotCounted() throws Exception {
		createRoutineOn(today, "오늘 것", "할일1", "할일2");
		createRoutineOn(today.minusDays(10), "열흘 전 것", "할일1", "할일2");

		// DAY 는 오늘만, WEEK 는 최근 7일이라 열흘 전 것은 둘 다에 들어가지 않는다.
		mockMvc.perform(statsRequest("DAY"))
			.andExpect(jsonPath("$.data.totalCount").value(2));

		mockMvc.perform(statsRequest("WEEK"))
			.andExpect(jsonPath("$.data.totalCount").value(2));

		// MONTH 는 최근 30일이라 들어간다.
		mockMvc.perform(statsRequest("MONTH"))
			.andExpect(jsonPath("$.data.totalCount").value(4));
	}

	@Test
	@DisplayName("WEEK 와 MONTH 의 기간이 문서대로 나온다")
	void weekAndMonthRangesMatchDocument() throws Exception {
		mockMvc.perform(statsRequest("WEEK"))
			.andExpect(jsonPath("$.data.from").value(today.minusDays(6).toString()))
			.andExpect(jsonPath("$.data.to").value(today.toString()));

		mockMvc.perform(statsRequest("MONTH"))
			.andExpect(jsonPath("$.data.from").value(today.minusDays(29).toString()))
			.andExpect(jsonPath("$.data.to").value(today.toString()));
	}

	@ParameterizedTest
	@DisplayName("알 수 없는 period 는 400 이다")
	@ValueSource(strings = {"YEAR", "WEEKLY", "3DAYS"})
	void unknownPeriodIsRejected(String period) throws Exception {
		mockMvc.perform(statsRequest(period))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("남의 자녀 통계는 볼 수 없다")
	void strangerCannotReadOthersStats() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/stats")
				.header(APP_HEADER, strangerUuid)
				.param("period", "DAY"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	// ------------------------------------------------------------------
	// 미션별 이행률
	// ------------------------------------------------------------------

	@Test
	@DisplayName("미션별 이행률이 series_id 로 묶인다")
	void missionStatsAreGroupedBySeriesId() throws Exception {
		// 사흘치 루틴을 만들면 같은 할 일이 사흘에 걸쳐 생기고,
		// 그것들이 하나의 미션으로 묶여야 한다.
		createRoutineRange(today.minusDays(2), today, "아침 준비", "세수하기", "양치하기");

		mockMvc.perform(missionStatsRequest("WEEK"))
			.andExpect(status().isOk())
			// 할 일이 2종류이므로 미션도 2개여야 한다. 날짜별로 흩어지면 6개가 된다.
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].totalCount").value(3));
	}

	@Test
	@DisplayName("이름을 바꿔도 한 미션으로 집계되고 최근 이름을 쓴다")
	void renamedMissionStaysOneRow() throws Exception {
		createRoutineRange(today.minusDays(2), today, "아침 준비", "세수하기");

		// 오늘 것 하나의 이름만 바꾼다. series_id 는 그대로다.
		long todayId = smallRoutineIdsOn(today).get(0);

		mockMvc.perform(patch("/api/v1/small-routines/" + todayId)
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "세수 깨끗이"}"""))
			.andExpect(status().isOk());

		// 이름을 고쳤다고 통계가 두 갈래로 갈라지면 "세수하기 미션을 얼마나
		// 하고 있나" 를 알 수 없게 된다.
		mockMvc.perform(missionStatsRequest("WEEK"))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].totalCount").value(3))
			.andExpect(jsonPath("$.data[0].title").value("세수 깨끗이"));
	}

	@Test
	@DisplayName("이행률이 낮은 미션이 먼저 나온다")
	void missionsAreSortedByLowestRate() throws Exception {
		createRoutineOn(today, "아침 준비", "잘하는것", "못하는것");

		// 첫 번째 할 일만 완료한다.
		completeSmallRoutines(today, 1);

		// 보호자가 보려는 것은 "무엇이 잘 안 되고 있나" 다.
		mockMvc.perform(missionStatsRequest("DAY"))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].title").value("못하는것"))
			.andExpect(jsonPath("$.data[0].completionRate").value(0.0))
			.andExpect(jsonPath("$.data[1].completionRate").value(100.0));
	}

	@Test
	@DisplayName("남의 자녀 미션 통계는 볼 수 없다")
	void strangerCannotReadOthersMissionStats() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/stats/missions")
				.header(APP_HEADER, strangerUuid)
				.param("period", "DAY"))
			.andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------
	// 대시보드
	// ------------------------------------------------------------------

	@Test
	@DisplayName("대시보드가 세 구간 이행률을 모두 준다")
	void dashboardReturnsAllThreePeriods() throws Exception {
		createRoutineOn(today, "아침 준비", "할일1", "할일2");
		completeSmallRoutines(today, 1);

		mockMvc.perform(get("/api/v1/children/" + childId + "/dashboard")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.childId").value(childId))
			.andExpect(jsonPath("$.data.childName").value("김아이"))
			.andExpect(jsonPath("$.data.insights.day.completionRate").value(50.0))
			.andExpect(jsonPath("$.data.insights.week.completionRate").value(50.0))
			.andExpect(jsonPath("$.data.insights.month.completionRate").value(50.0))
			.andExpect(jsonPath("$.data.insights.day.from").value(today.toString()))
			.andExpect(jsonPath("$.data.insights.week.from")
				.value(today.minusDays(6).toString()));
	}

	@Test
	@DisplayName("대시보드의 주간 수치는 stats 와 같다")
	void dashboardWeekMatchesStatsEndpoint() throws Exception {
		createRoutineRange(today.minusDays(3), today, "아침 준비", "할일1", "할일2");
		completeSmallRoutines(today, 1);

		String statsBody = mockMvc.perform(statsRequest("WEEK"))
			.andReturn().getResponse().getContentAsString();
		String dashboardBody = mockMvc.perform(get("/api/v1/children/" + childId + "/dashboard")
				.header(APP_HEADER, ownerUuid))
			.andReturn().getResponse().getContentAsString();

		JsonNode stats = objectMapper.readTree(statsBody).path("data");
		JsonNode week = objectMapper.readTree(dashboardBody).path("data")
			.path("insights").path("week");

		// 두 곳이 각자 날짜를 계산하면 하루가 어긋날 수 있고, 그 차이는
		// 눈으로 보고는 알아차리기 어렵다.
		assertThat(week.path("from").asString()).isEqualTo(stats.path("from").asString());
		assertThat(week.path("totalCount").asInt()).isEqualTo(stats.path("totalCount").asInt());
		assertThat(week.path("doneCount").asInt()).isEqualTo(stats.path("doneCount").asInt());
	}

	@Test
	@DisplayName("대시보드에 devices 배열이 있고 단일 battery 필드는 없다")
	void dashboardHasDevicesArrayNotSingleBattery() throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/dashboard")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.devices").isArray())
			.andReturn().getResponse().getContentAsString();

		// 기기가 여러 대일 때 단일 battery 값이 어느 기기 것인지 표현할 수 없다.
		// breaking change 라 되돌아가지 않게 테스트로 못 박는다.
		JsonNode data = objectMapper.readTree(body).path("data");

		assertThat(data.has("battery")).isFalse();
	}

	@Test
	@DisplayName("아직 sync 하지 않은 기기의 배터리는 null 그대로 나간다")
	void unsyncedDeviceKeepsNullBattery() throws Exception {
		mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andExpect(status().isCreated());

		// 0 으로 채우면 "배터리 없음" 과 "아직 모름" 을 구분할 수 없다.
		mockMvc.perform(get("/api/v1/children/" + childId + "/dashboard")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.devices.length()").value(1))
			.andExpect(jsonPath("$.data.devices[0].status").value("PENDING"))
			.andExpect(jsonPath("$.data.devices[0].battery").doesNotExist())
			.andExpect(jsonPath("$.data.devices[0].lastSyncedAt").doesNotExist());
	}

	@Test
	@DisplayName("남의 자녀 대시보드는 볼 수 없다")
	void strangerCannotReadOthersDashboard() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/dashboard")
				.header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private RequestBuilder statsRequest(String period) {
		return get("/api/v1/children/" + childId + "/stats")
			.header(APP_HEADER, ownerUuid)
			.param("period", period);
	}

	private RequestBuilder missionStatsRequest(String period) {
		return get("/api/v1/children/" + childId + "/stats/missions")
			.header(APP_HEADER, ownerUuid)
			.param("period", period);
	}

	private String signUp(String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("accessUuid").asString();
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

	private void createRoutineOn(LocalDate date, String title, String... smallRoutineTitles)
		throws Exception {

		createRoutineRange(date, date, title, smallRoutineTitles);
	}

	private void createRoutineRange(
		LocalDate from, LocalDate to, String title, String... smallRoutineTitles)
		throws Exception {

		StringBuilder smallRoutines = new StringBuilder();

		for (int index = 0; index < smallRoutineTitles.length; index++) {
			smallRoutines.append("{\"title\": \"%s\"}".formatted(smallRoutineTitles[index]));
			if (index < smallRoutineTitles.length - 1) {
				smallRoutines.append(", ");
			}
		}

		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, ownerUuid)
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
					}""".formatted(title, from, to, smallRoutines)))
			.andExpect(status().isCreated());
	}

	private java.util.List<Long> smallRoutineIdsOn(LocalDate date) throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", date.toString())
				.param("to", date.toString()))
			.andReturn().getResponse().getContentAsString();

		JsonNode smallRoutines = objectMapper.readTree(body)
			.path("data").get(0).path("bigRoutines").get(0).path("smallRoutines");

		java.util.List<Long> ids = new java.util.ArrayList<>();
		smallRoutines.forEach(node -> ids.add(node.path("smallRoutineId").asLong()));

		return ids;
	}

	/**
	 * 그 날짜의 할 일 중 앞에서 몇 개를 완료 처리한다.
	 *
	 * 기기 sync 를 거치지 않고 앱 경로로 넣는 이유는, 여기서 확인하려는 것이
	 * 완료가 어떻게 들어왔는지가 아니라 그것이 어떻게 집계되는지이기 때문이다.
	 */
	private void completeSmallRoutines(LocalDate date, int count) throws Exception {
		java.util.List<Long> ids = smallRoutineIdsOn(date);

		for (int index = 0; index < count; index++) {
			markDone(ids.get(index));
		}
	}

	private void markDone(long smallRoutineId) {
		jdbcMarkDone(smallRoutineId);
	}

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	/**
	 * 완료 상태를 직접 넣는다.
	 *
	 * 앱에는 "할 일을 완료로 바꾸는" 엔드포인트가 없다. 완료는 아이가 기기에서
	 * 하는 일이고 sync 로만 올라온다. 여기서 sync 를 거치려면 페어링부터
	 * 해야 하는데, 이 테스트가 확인하려는 것은 집계 규칙이지 완료 경로가 아니다.
	 */
	private void jdbcMarkDone(long smallRoutineId) {
		jdbcTemplate.update(
			"update small_routines set status = 'DONE', completed_at = now() where id = ?",
			smallRoutineId);
	}

}
