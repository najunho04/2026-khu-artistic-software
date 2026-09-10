package artistic.software.khu.artistic_software_khu.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;

/**
 * 같은 자원에 요청이 "동시에" 들어왔을 때를 확인한다.
 *
 * 이 시나리오들은 실제로 일어난다. 기기는 네트워크가 끊기면 응답을 못 받은
 * 요청을 다시 보내는데, 앞의 요청이 죽은 것이 아니라 늦게 도착하는 중일 수
 * 있다. 그러면 같은 완료 기록이 두 번 처리된다.
 *
 * 이 클래스만 트랜잭션을 걸지 않는다. 테스트가 하나의 트랜잭션 안에서 돌면
 * 두 스레드가 같은 트랜잭션을 나눠 쓰게 되어 "동시" 를 재현할 수 없기 때문이다.
 * 대신 데이터가 남으므로 이메일과 기기 uid 에 매번 다른 값을 쓴다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ParallelRequestIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String PASSWORD = "password1234";

	private static final String ROUTINE_DATE = "2099-03-03";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("같은 완료 기록이 동시에 두 번 올라와도 경험치는 1만 오른다")
	void concurrentSyncGrantsExperienceOnce() throws Exception {
		String ownerUuid = signUp();
		long childId = registerChild(ownerUuid);
		String deviceAccessUuid = pairAndClaim(ownerUuid, childId);

		insertCharacterIfAbsent();
		createRoutine(ownerUuid, childId);

		long smallRoutineId = firstSmallRoutineId(ownerUuid, childId);
		String request = """
			{
			  "battery": 70, "firmware": "1.0.0",
			  "completions": [
			    {"smallRoutineId": %d, "status": "DONE", "completedAt": "2099-03-03T07:42:00Z"}
			  ],
			  "dates": ["%s"]
			}""".formatted(smallRoutineId, ROUTINE_DATE);

		runInParallel(
			() -> mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request)).andReturn().getResponse().getStatus());

		// 완료된 할 일은 하나뿐이므로 경험치도 1 이어야 한다. 두 요청이 모두
		// "이번에 처음 완료됐다" 고 판단하면 아이의 성장이 네트워크 상태에
		// 좌우된다. "API.md" 12-1 이 정한 멱등성이 깨지는 지점이다.
		Integer totalExperience = jdbcTemplate.queryForObject(
			"select coalesce(sum(exp), 0) from child_characters where child_id = ?",
			Integer.class, childId);

		assertThat(totalExperience).isEqualTo(1);

		// 캐릭터도 한 마리만 지급돼야 한다. 두 요청이 각자 "보유 0마리" 를
		// 보면 같은 캐릭터가 두 줄 생긴다.
		Integer characterCount = jdbcTemplate.queryForObject(
			"select count(*) from child_characters where child_id = ?", Integer.class, childId);

		assertThat(characterCount).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 페어링 코드로 동시에 claim 하면 한 대만 성공한다")
	void concurrentClaimSucceedsOnlyOnce() throws Exception {
		String ownerUuid = signUp();
		long childId = registerChild(ownerUuid);
		String pairingCode = startPairing(ownerUuid, childId);

		String firstDeviceUid = "parallel-" + UUID.randomUUID();
		String secondDeviceUid = "parallel-" + UUID.randomUUID();

		List<Integer> statuses = runInParallel(
			() -> claim(pairingCode, firstDeviceUid),
			() -> claim(pairingCode, secondDeviceUid));

		// 코드는 일회용이다("API.md" 8장). 둘 다 200 을 받으면 나중 것만
		// 저장되고, 먼저 받은 기기는 저장되지 않은 신분증을 들고 있게 된다.
		// 그 기기는 이후 모든 요청에서 DEVICE_UNAUTHORIZED 를 받는데,
		// 자기가 성공했다고 믿고 있어 재페어링 안내가 뜨지 않는다.
		assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("기기 상한에 걸린 자녀에게 동시에 페어링해도 한 대만 늘어난다")
	void concurrentPairingRespectsDeviceLimit() throws Exception {
		String ownerUuid = signUp();
		long childId = registerChild(ownerUuid);

		// 상한은 자녀당 10대다("API.md" 3-4). 9대를 채워 두고 두 요청을
		// 동시에 보내면 한 대만 들어가야 한다.
		for (int count = 0; count < 9; count++) {
			startPairing(ownerUuid, childId);
		}

		runInParallel(() -> startPairingStatus(ownerUuid, childId));

		Integer deviceCount = jdbcTemplate.queryForObject(
			"select count(*) from devices where child_id = ? and deleted_at is null",
			Integer.class, childId);

		assertThat(deviceCount).isEqualTo(10);
	}

	@Test
	@DisplayName("같은 빅루틴에 할 일을 동시에 더해도 순서가 겹치지 않는다")
	void concurrentSmallRoutineAdditionsGetDistinctOrders() throws Exception {
		String ownerUuid = signUp();
		long childId = registerChild(ownerUuid);

		createRoutine(ownerUuid, childId);

		long bigRoutineId = firstBigRoutineId(ownerUuid, childId);

		runInParallel(() -> mockMvc.perform(
			post("/api/v1/big-routines/" + bigRoutineId + "/small-routines")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "가방 챙기기"}"""))
			.andReturn().getResponse().getStatus());

		// 새 순서는 "지금까지 쓴 가장 큰 값 + 1" 로 정한다. 두 요청이 같은
		// 값을 읽으면 둘 다 같은 번호를 쓰고, 스몰루틴의 미션 식별자가
		// "빅루틴 시리즈 + 순서" 로 계산되므로 서로 다른 두 할 일이 같은
		// 미션이 된다. 미션별 통계에서 한 줄로 합쳐져 보호자 눈에는 할 일
		// 하나가 사라진 것처럼 보인다.
		Integer distinctOrderCount = jdbcTemplate.queryForObject(
			"select count(distinct sort_order) from small_routines where big_routine_id = ?",
			Integer.class, bigRoutineId);

		Integer smallRoutineCount = jdbcTemplate.queryForObject(
			"select count(*) from small_routines where big_routine_id = ?",
			Integer.class, bigRoutineId);

		assertThat(distinctOrderCount).isEqualTo(smallRoutineCount);

		Integer distinctSeriesCount = jdbcTemplate.queryForObject(
			"select count(distinct series_id) from small_routines where big_routine_id = ?",
			Integer.class, bigRoutineId);

		assertThat(distinctSeriesCount).isEqualTo(smallRoutineCount);
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private long firstBigRoutineId(String ownerUuid, long childId) throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", ROUTINE_DATE)
				.param("to", ROUTINE_DATE))
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").get(0)
			.path("bigRoutines").get(0).path("bigRoutineId").asLong();
	}

	/**
	 * 같은 일을 두 스레드에서 동시에 시작한다.
	 *
	 * 장벽(CyclicBarrier)을 쓰는 이유는 두 요청이 정말 겹치게 하기 위해서다.
	 * 그냥 순서대로 제출하면 앞의 요청이 끝난 뒤에 뒤의 요청이 시작될 수 있어
	 * 동시 상황이 재현되지 않는다.
	 */
	@SafeVarargs
	private List<Integer> runInParallel(Callable<Integer>... tasks) throws Exception {
		int taskCount = (tasks.length == 1) ? 2 : tasks.length;
		CyclicBarrier barrier = new CyclicBarrier(taskCount);
		ExecutorService executor = Executors.newFixedThreadPool(taskCount);

		try {
			List<Future<Integer>> futures = new ArrayList<>();

			for (int index = 0; index < taskCount; index++) {
				Callable<Integer> task = (tasks.length == 1) ? tasks[0] : tasks[index];

				futures.add(executor.submit(() -> {
					barrier.await(10, TimeUnit.SECONDS);
					return task.call();
				}));
			}

			List<Integer> statuses = new ArrayList<>();

			for (Future<Integer> future : futures) {
				statuses.add(future.get(30, TimeUnit.SECONDS));
			}

			return statuses;
		} finally {
			executor.shutdownNow();
		}
	}

	private int claim(String pairingCode, String deviceUid) throws Exception {
		return mockMvc.perform(post("/device-api/v1/claim")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"pairingCode": "%s", "deviceUid": "%s", "firmware": "1.0.0"}"""
					.formatted(pairingCode, deviceUid)))
			.andReturn().getResponse().getStatus();
	}

	private String signUp() throws Exception {
		String email = "parallel-" + UUID.randomUUID() + "@example.com";

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

	private String startPairing(String ownerUuid, long childId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("pairingCode").asText();
	}

	private int startPairingStatus(String ownerUuid, long childId) throws Exception {
		return mockMvc.perform(post("/api/v1/devices/pairing")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"childId": %d, "nickname": "예소"}""".formatted(childId)))
			.andReturn().getResponse().getStatus();
	}

	private String pairAndClaim(String ownerUuid, long childId) throws Exception {
		String pairingCode = startPairing(ownerUuid, childId);

		String claimBody = mockMvc.perform(post("/device-api/v1/claim")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"pairingCode": "%s", "deviceUid": "%s", "firmware": "1.0.0"}"""
					.formatted(pairingCode, "parallel-" + UUID.randomUUID())))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(claimBody).path("data").path("deviceAccessUuid").asText();
	}

	/**
	 * 도감에 캐릭터가 한 마리라도 있어야 경험치가 들어갈 자리가 생긴다.
	 *
	 * "characters" 는 디자인 산출물이 나오기 전까지 비어 있어서("API.md" 12-1)
	 * 테스트가 직접 한 줄을 넣는다.
	 */
	private void insertCharacterIfAbsent() {
		jdbcTemplate.update("""
			insert into characters (code, name, rarity, weight, assets, is_active)
			values ('TEST_01', '테스트 캐릭터', 'COMMON', 1, null, true)
			on conflict (code) do nothing""");
	}

	private void createRoutine(String ownerUuid, long childId) throws Exception {
		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "아침 준비",
					  "startTime": "07:30",
					  "endTime": "08:30",
					  "repeatType": "RANGE",
					  "startDate": "%s",
					  "endDate": "%s",
					  "smallRoutines": [{"title": "세수하기"}]
					}""".formatted(ROUTINE_DATE, ROUTINE_DATE)))
			.andExpect(status().isCreated());
	}

	private long firstSmallRoutineId(String ownerUuid, long childId) throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", ROUTINE_DATE)
				.param("to", ROUTINE_DATE))
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").get(0)
			.path("bigRoutines").get(0).path("smallRoutines").get(0)
			.path("smallRoutineId").asLong();
	}

}
