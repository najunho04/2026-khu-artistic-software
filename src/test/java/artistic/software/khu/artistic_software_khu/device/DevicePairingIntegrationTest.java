package artistic.software.khu.artistic_software_khu.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 페어링과 claim. "API.md" 7장 · 8장, 그리고 14장의 온보딩 순서.
 *
 * 이 구간의 특징은 **앱과 기기가 서로 다른 인증으로 같은 행 하나를 주고받는다**는
 * 점이다. 앱이 PENDING 행을 만들고 코드를 핫스팟으로 넘기면, 기기가 그 코드로
 * 같은 행을 찾아 ACTIVE 로 바꾼다. 그래서 두 방향을 한 시나리오로 확인한다.
 *
 * `claim` 은 **인증이 없는 엔드포인트**다. 누구나 코드를 넣어볼 수 있으므로,
 * 만료·재사용·없는 코드를 각각 거절하는지가 특히 중요하다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DevicePairingIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String PASSWORD = "password1234";

	private String ownerUuid;

	private String strangerUuid;

	private long childId;

	private long strangerChildId;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	// 영속성 컨텍스트를 비우는 데 쓴다. 아래 만료 테스트의 주석 참고.
	@PersistenceContext
	private EntityManager entityManager;

	@BeforeEach
	void prepareGuardianAndChild() throws Exception {
		ownerUuid = signUp("pairing-owner@example.com");
		strangerUuid = signUp("pairing-stranger@example.com");
		childId = registerChild(ownerUuid);
		strangerChildId = registerChild(strangerUuid);
	}

	// ------------------------------------------------------------------
	// 페어링 코드 발급 (앱)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("페어링을 요청하면 열 자리 코드와 deviceId 를 받는다")
	void issuePairingCodeReturnsTenDigitCodeAndDeviceId() throws Exception {
		mockMvc.perform(pairingRequest(ownerUuid, childId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.deviceId").isNumber())
			// 자릿수가 흔들리면 기기 쪽 파싱이 어긋난다.
			.andExpect(jsonPath("$.data.pairingCode").value(
				org.hamcrest.Matchers.matchesPattern("\\d{10}")))
			.andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
			.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	@DisplayName("자녀 한 명에게 페어링을 두 번 요청하면 서로 다른 코드가 나온다")
	void twoPairingRequestsGetDifferentCodes() throws Exception {
		// 자녀당 기기가 여러 대이므로 PENDING 행이 동시에 여럿일 수 있다.
		String firstCode = pairingCodeOf(ownerUuid, childId);
		String secondCode = pairingCodeOf(ownerUuid, childId);

		assertThat(firstCode).isNotEqualTo(secondCode);
	}

	@Test
	@DisplayName("기기는 자녀당 열 대까지고 열한 번째는 409 다")
	void deviceLimitIsTen() throws Exception {
		for (int index = 1; index <= 10; index++) {
			mockMvc.perform(pairingRequest(ownerUuid, childId))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(pairingRequest(ownerUuid, childId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEVICE_LIMIT_EXCEEDED"));
	}

	@Test
	@DisplayName("남의 자녀에게는 페어링을 요청할 수 없다")
	void strangerCannotIssuePairingForOthersChild() throws Exception {
		mockMvc.perform(pairingRequest(strangerUuid, childId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	// ------------------------------------------------------------------
	// claim (기기) — 인증 없는 엔드포인트
	// ------------------------------------------------------------------

	@Test
	@DisplayName("기기가 코드로 claim 하면 deviceAccessUuid 를 받는다")
	void deviceClaimsWithPairingCode() throws Exception {
		String pairingCode = pairingCodeOf(ownerUuid, childId);

		mockMvc.perform(claimRequest(pairingCode, "device-uid-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.deviceId").isNumber())
			.andExpect(jsonPath("$.data.deviceAccessUuid").isNotEmpty())
			.andExpect(jsonPath("$.data.serverTime").isNotEmpty());
	}

	@Test
	@DisplayName("claim 하면 상태가 ACTIVE 로 바뀌고 코드는 비워진다")
	void claimActivatesDeviceAndClearsCode() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();
		String pairingCode = pairingCodeById(deviceId);

		mockMvc.perform(claimRequest(pairingCode, "device-uid-0002"))
			.andExpect(status().isOk());

		String status = jdbcTemplate.queryForObject(
			"select status from devices where id = ?", String.class, deviceId);
		String remainingCode = jdbcTemplate.queryForObject(
			"select pairing_code from devices where id = ?", String.class, deviceId);

		assertThat(status).isEqualTo("ACTIVE");
		// 일회용이다. 비우지 않으면 같은 코드로 다른 기기가 또 붙을 수 있다.
		assertThat(remainingCode).isNull();
	}

	@Test
	@DisplayName("같은 코드로 두 번 claim 할 수 없다")
	void sameCodeCannotBeClaimedTwice() throws Exception {
		String pairingCode = pairingCodeOf(ownerUuid, childId);

		mockMvc.perform(claimRequest(pairingCode, "device-uid-0003"))
			.andExpect(status().isOk());

		// 코드가 비워졌으므로 두 번째는 찾을 수 없다.
		mockMvc.perform(claimRequest(pairingCode, "device-uid-0004"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_NOT_FOUND"));
	}

	@Test
	@DisplayName("없는 코드로 claim 하면 404 다")
	void unknownPairingCodeIsRejected() throws Exception {
		// 인증이 없는 엔드포인트라 누구나 무작위로 넣어볼 수 있다.
		// 10자리(100억 조합)로 정한 것이 이 때문이다.
		mockMvc.perform(claimRequest("0000000000", "device-uid-0005"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_NOT_FOUND"));
	}

	@Test
	@DisplayName("만료된 코드로 claim 하면 410 이다")
	void expiredPairingCodeIsRejected() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();
		String pairingCode = pairingCodeById(deviceId);

		// 만료 시각을 과거로 돌려 만료를 흉내 낸다. 실제로 10분을 기다릴 수 없다.
		jdbcTemplate.update(
			"update devices set pairing_code_expires_at = now() - interval '1 minute'"
				+ " where id = ?", deviceId);

		// 영속성 컨텍스트를 비운다.
		//
		// 이 테스트는 @Transactional 이라 발급과 claim 이 한 트랜잭션 안에서
		// 돈다. 그래서 위의 SQL 로 DB 를 고쳐도, JPA 가 이미 들고 있는 객체가
		// 조회 결과를 덮어써 옛 만료 시각이 그대로 쓰인다.
		//
		// 실제 운영에서는 발급과 claim 이 서로 다른 요청이라 영속성 컨텍스트가
		// 따로 뜨고 이 문제가 없다. 비우는 것은 그 상황을 흉내 내는 것이다.
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(claimRequest(pairingCode, "device-uid-0006"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("PAIRING_CODE_EXPIRED"));
	}

	@Test
	@DisplayName("claim 은 인증 없이 호출된다")
	void claimRequiresNoAuthentication() throws Exception {
		String pairingCode = pairingCodeOf(ownerUuid, childId);

		// 이 시점의 기기는 아직 아무 신분증도 없다. 인증을 걸면 페어링 자체가
		// 불가능해진다. "API.md" 1-1 의 화이트리스트에 들어 있는 이유다.
		mockMvc.perform(claimRequest(pairingCode, "device-uid-0007"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("claim 으로 받은 값으로 기기 API 를 부를 수 있다")
	void claimedDeviceCanCallDeviceApi() throws Exception {
		String pairingCode = pairingCodeOf(ownerUuid, childId);

		String body = mockMvc.perform(claimRequest(pairingCode, "device-uid-0008"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		String deviceAccessUuid =
			objectMapper.readTree(body).path("data").path("deviceAccessUuid").asText();

		// 온보딩의 마지막 조각이다. 여기까지 이어져야 기기가 루틴을 받아 갈 수 있다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 50, "firmware": "1.0.0", "completions": [], "dates": []}"""))
			.andExpect(status().isOk());
	}

	// ------------------------------------------------------------------
	// 폴링 · 기기 관리 (앱)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("앱이 상태를 폴링하면 PENDING 에서 ACTIVE 로 바뀐다")
	void appPollsUntilDeviceBecomesActive() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();

		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PENDING"));

		mockMvc.perform(claimRequest(pairingCodeById(deviceId), "device-uid-0009"))
			.andExpect(status().isOk());

		// 핫스팟 전달이 단방향이라 앱은 성공 여부를 모른다. 그래서 폴링한다.
		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	@DisplayName("상태 응답에 페어링 코드가 들어가지 않는다")
	void deviceStatusResponseNeverExposesPairingCode() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();

		String body = mockMvc.perform(get("/api/v1/devices/" + deviceId)
				.header(APP_HEADER, ownerUuid))
			.andReturn().getResponse().getContentAsString();

		// 발급 응답에서 한 번 준 것으로 끝이다. 조회 때마다 다시 실어 보내면
		// 코드가 살아 있는 10분 동안 여러 경로로 새어 나갈 자리가 늘어난다.
		assertThat(body).doesNotContain("pairingCode");
	}

	@Test
	@DisplayName("자녀의 기기 목록을 조회한다")
	void listDevicesOfChild() throws Exception {
		issuePairingAndGetDeviceId();
		issuePairingAndGetDeviceId();

		mockMvc.perform(get("/api/v1/children/" + childId + "/devices")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	@DisplayName("기기 별칭을 바꾼다")
	void updateDeviceNickname() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();

		mockMvc.perform(patch("/api/v1/devices/" + deviceId)
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname": "거실 기기"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("거실 기기"));
	}

	@Test
	@DisplayName("기기 연결을 해제하면 그 값으로 더는 인증되지 않는다")
	void releasedDeviceCannotAuthenticate() throws Exception {
		String pairingCode = pairingCodeOf(ownerUuid, childId);

		String body = mockMvc.perform(claimRequest(pairingCode, "device-uid-0010"))
			.andReturn().getResponse().getContentAsString();

		JsonNode data = objectMapper.readTree(body).path("data");
		long deviceId = data.path("deviceId").asLong();
		String deviceAccessUuid = data.path("deviceAccessUuid").asText();

		mockMvc.perform(delete("/api/v1/devices/" + deviceId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		// 해제한 기기가 계속 인증에 성공하면 그 기기는 주인이 끊었는데도
		// 자녀의 루틴을 계속 받아 간다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 50, "firmware": "1.0.0", "completions": [], "dates": []}"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("해제한 뒤에는 같은 device_uid 로 다시 페어링할 수 있다")
	void sameDeviceCanBePairedAgainAfterRelease() throws Exception {
		String firstCode = pairingCodeOf(ownerUuid, childId);

		String body = mockMvc.perform(claimRequest(firstCode, "device-uid-0011"))
			.andReturn().getResponse().getContentAsString();

		long deviceId = objectMapper.readTree(body).path("data").path("deviceId").asLong();

		mockMvc.perform(delete("/api/v1/devices/" + deviceId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		// unique(device_uid) 에 "where deleted_at is null" 이 붙는 이유다.
		// 조건이 빠지면 한 번 해제한 기기는 영원히 다시 연결할 수 없다.
		String secondCode = pairingCodeOf(ownerUuid, childId);

		mockMvc.perform(claimRequest(secondCode, "device-uid-0011"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("남의 기기는 조회 · 수정 · 해제할 수 없다")
	void strangerCannotTouchOthersDevice() throws Exception {
		long deviceId = issuePairingAndGetDeviceId();

		mockMvc.perform(get("/api/v1/devices/" + deviceId).header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/devices/" + deviceId)
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname": "훔치기"}"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/v1/devices/" + deviceId).header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("남의 자녀 기기 목록은 볼 수 없다")
	void strangerCannotListOthersDevices() throws Exception {
		issuePairingAndGetDeviceId();

		mockMvc.perform(get("/api/v1/children/" + childId + "/devices")
				.header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
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

	private org.springframework.test.web.servlet.RequestBuilder pairingRequest(
		String accessUuid, long targetChildId) {

		return post("/api/v1/devices/pairing")
			.header(APP_HEADER, accessUuid)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"childId": %d, "nickname": "예소"}""".formatted(targetChildId));
	}

	private org.springframework.test.web.servlet.RequestBuilder claimRequest(
		String pairingCode, String deviceUid) {

		return post("/device-api/v1/claim")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"pairingCode": "%s", "deviceUid": "%s", "firmware": "1.0.0"}"""
				.formatted(pairingCode, deviceUid));
	}

	private String pairingCodeOf(String accessUuid, long targetChildId) throws Exception {
		String body = mockMvc.perform(pairingRequest(accessUuid, targetChildId))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("pairingCode").asText();
	}

	private long issuePairingAndGetDeviceId() throws Exception {
		String body = mockMvc.perform(pairingRequest(ownerUuid, childId))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("deviceId").asLong();
	}

	/** 발급 응답을 놓친 경우 DB 에서 코드를 다시 꺼낸다. */
	private String pairingCodeById(long deviceId) {
		return jdbcTemplate.queryForObject(
			"select pairing_code from devices where id = ?", String.class, deviceId);
	}

}
