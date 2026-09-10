package artistic.software.khu.artistic_software_khu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;

/**
 * 회원 탈퇴. "API.md" 5장 DELETE /users/me, 로드맵 6-1.
 *
 * 탈퇴는 보호자에게 딸린 것을 전부 함께 지운다. 자녀 · 기기 · 빅루틴 ·
 * 스몰루틴 · 양식이며 전부 soft delete("deleted_at" 을 채워 지운 표시만 남기는
 * 삭제) 다("ERD.md" 5-7).
 *
 * 이 테스트가 확인해야 하는 것 중 가장 중요한 것은 "무엇이 남았는가" 다.
 * 계정만 지워지고 기기가 살아남으면 그 기기는 주인 없는 상태로 계속 인증에
 * 성공한다. 그래서 앱 토큰뿐 아니라 기기 토큰까지 죽었는지 함께 본다.
 *
 * 지우는 대상이 다섯 가지라 단언도 다섯 개를 단다. 하나만 확인하면 절반만
 * 지우는 구현이 초록불로 통과한다(history H-007).
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserWithdrawalIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String EMAIL = "withdrawal@example.com";

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

	/**
	 * 지울 것이 전부 갖춰진 보호자를 만든다.
	 *
	 * 자녀 한 명, 그 자녀에 붙은 기기 한 대, 빅루틴과 스몰루틴, 양식 하나까지
	 * 만들어 둔다. 하나라도 빠지면 그 종류가 안 지워지는 구현을 잡지 못한다.
	 */
	@BeforeEach
	void prepareGuardianWithEverything() throws Exception {
		ownerUuid = signUp(EMAIL);
		childId = registerChild(ownerUuid);
		deviceAccessUuid = pairAndClaim("device-uid-withdrawal-1");
		createRoutine(ownerUuid, childId, "아침 준비", "세수하기", "양치하기");
		createTemplate(ownerUuid, childId, "저녁 준비");
	}

	@Test
	@DisplayName("탈퇴하면 204 로 응답하고 본문이 없다")
	void withdrawalRespondsNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
	}

	@Test
	@DisplayName("탈퇴한 뒤 같은 접속 값으로 호출하면 401 이다")
	void accessUuidStopsWorkingAfterWithdrawal() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		// 만료가 없는 값이라 비우는 것이 무효화하는 유일한 수단이다.
		// 비우지 않으면 탈퇴한 계정의 토큰이 영원히 살아 있다.
		mockMvc.perform(get("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("탈퇴하면 자녀 · 빅루틴 · 스몰루틴 · 양식이 전부 지워진다")
	void withdrawalSoftDeletesEverythingUnderTheGuardian() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		flushSoThatSqlSeesTheChanges();

		assertThat(liveCount("children", "id", childId)).isZero();
		assertThat(liveCount("big_routines", "child_id", childId)).isZero();
		assertThat(liveSmallRoutineCount(childId)).isZero();
		assertThat(liveCount("routine_templates", "child_id", childId)).isZero();
		assertThat(liveCount("users", "id", userId(EMAIL))).isZero();
	}

	@Test
	@DisplayName("탈퇴하면 기기도 해제되어 동기화가 401 이 된다")
	void deviceStopsSyncingAfterWithdrawal() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		flushSoThatSqlSeesTheChanges();

		assertThat(liveCount("devices", "child_id", childId)).isZero();

		// 기기는 앱과 다른 값으로 인증하므로 앱 토큰을 죽인 것만으로는 막히지
		// 않는다. 주인이 사라진 기기가 계속 루틴을 받아 가면 안 된다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 70, "firmware": "1.0.3", "completions": [], "dates": ["%s"]}"""
					.formatted(ROUTINE_DATE)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("DEVICE_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("탈퇴한 이메일로 다시 가입할 수 있고 예전 자녀는 딸려오지 않는다")
	void sameEmailCanSignUpAgainWithCleanSlate() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		flushSoThatSqlSeesTheChanges();

		// "USERS" 의 이메일 유일 제약이 "unique(email) where deleted_at is null"
		// 이라 지워진 행은 제약에서 빠진다("ERD.md" 4장).
		String rejoinedUuid = signUp(EMAIL);

		// 새 계정이다. 같은 이메일이라고 예전 자녀가 붙어 오면 탈퇴가 아니라
		// 잠깐 로그아웃한 것이나 다름없어진다.
		mockMvc.perform(get("/api/v1/children").header(APP_HEADER, rejoinedUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("남의 자녀와 루틴은 지워지지 않는다")
	void otherGuardiansDataSurvives() throws Exception {
		String otherUuid = signUp("withdrawal-other@example.com");
		long otherChildId = registerChild(otherUuid);
		createRoutine(otherUuid, otherChildId, "남의 루틴", "남의 할일");

		mockMvc.perform(delete("/api/v1/users/me").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		flushSoThatSqlSeesTheChanges();

		// 지울 대상을 "보호자의 자녀" 가 아니라 "모든 자녀" 로 잡는 실수를 잡는다.
		assertThat(liveCount("children", "id", otherChildId)).isOne();
		assertThat(liveCount("big_routines", "child_id", otherChildId)).isOne();
		assertThat(liveSmallRoutineCount(otherChildId)).isOne();

		mockMvc.perform(get("/api/v1/users/me").header(APP_HEADER, otherUuid))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("인증 헤더 없이 탈퇴를 부르면 401 이다")
	void withdrawalRequiresAuthentication() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	/**
	 * 하이버네이트가 들고 있는 변경을 DB 로 내려보낸다.
	 *
	 * 아래 확인은 JPA 가 아니라 SQL 로 직접 세는데, 하이버네이트는 트랜잭션이
	 * 끝날 때까지 UPDATE 를 미룰 수 있다. 내려보내지 않으면 "지워지지 않았다" 는
	 * 잘못된 실패가 난다.
	 */
	private void flushSoThatSqlSeesTheChanges() {
		entityManager.flush();
		entityManager.clear();
	}

	private int liveCount(String tableName, String columnName, long value) {
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from " + tableName
				+ " where " + columnName + " = ? and deleted_at is null",
			Integer.class, value);

		return count == null ? 0 : count;
	}

	/** 스몰루틴은 자녀를 직접 가리키지 않고 빅루틴을 통해 매달려 있다. */
	private int liveSmallRoutineCount(long targetChildId) {
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from small_routines smallRoutine"
				+ " join big_routines bigRoutine on bigRoutine.id = smallRoutine.big_routine_id"
				+ " where bigRoutine.child_id = ? and smallRoutine.deleted_at is null",
			Integer.class, targetChildId);

		return count == null ? 0 : count;
	}

	private long userId(String email) {
		Long id = jdbcTemplate.queryForObject(
			"select id from users where email = ? order by id desc limit 1", Long.class, email);

		return id == null ? 0L : id;
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

	private void createRoutine(
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

	private void createTemplate(String accessUuid, long targetChildId, String title)
		throws Exception {

		mockMvc.perform(post("/api/v1/children/" + targetChildId + "/routine-templates")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "%s",
					  "startTime": "19:00",
					  "endTime": "20:00",
					  "smallRoutines": [{"title": "숙제하기"}]
					}""".formatted(title)))
			.andExpect(status().isCreated());
	}

}
