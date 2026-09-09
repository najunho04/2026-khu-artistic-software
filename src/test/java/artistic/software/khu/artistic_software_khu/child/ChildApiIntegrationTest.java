package artistic.software.khu.artistic_software_khu.child;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
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
 * 자녀 API. "API.md" 6장과 1-1-1 소유권 검사.
 *
 * 이 구간에서 가장 중요한 것은 CRUD 가 아니라 **소유권 검사**다. 경로에 자녀 id
 * 가 그대로 드러나 있어서, 검사가 없으면 id 를 1, 2, 3 으로 바꿔가며 남의 아이
 * 정보를 전부 읽을 수 있다. 그리고 이 실수는 기능이 잘 도는 것처럼 보여
 * 눈으로는 잡히지 않는다. 그래서 "남의 자녀" 를 만들어 실제로 찔러 본다.
 *
 * Phase 3 루틴 API 가 전부 이 검사 위에 올라가므로 여기서 확실히 고정해 둔다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildApiIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String OWNER_EMAIL = "owner@example.com";

	private static final String STRANGER_EMAIL = "stranger@example.com";

	private static final String PASSWORD = "password1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	// 자녀를 소유한 보호자의 접속 값
	private String ownerUuid;

	// 아무 관계도 없는 다른 보호자의 접속 값. 소유권 검사를 찔러 보는 데 쓴다.
	private String strangerUuid;

	@BeforeEach
	void signUpTwoGuardians() throws Exception {
		ownerUuid = signUp(OWNER_EMAIL);
		strangerUuid = signUp(STRANGER_EMAIL);
	}

	// ------------------------------------------------------------------
	// 등록
	// ------------------------------------------------------------------

	@Test
	@DisplayName("자녀를 등록하면 201 이고 등록한 값이 그대로 돌아온다")
	void registerChildReturnsCreated() throws Exception {
		mockMvc.perform(registerRequest(ownerUuid, "김아이", "2018-03-02", "PARENT"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.childId").isNumber())
			.andExpect(jsonPath("$.data.name").value("김아이"))
			.andExpect(jsonPath("$.data.birthDate").value("2018-03-02"))
			.andExpect(jsonPath("$.data.relationship").value("PARENT"));
	}

	@ParameterizedTest
	@DisplayName("relationship 은 PARENT 와 ADMIN 만 받는다")
	@ValueSource(strings = {"PARENT", "ADMIN"})
	void relationshipAcceptsOnlyConfirmedValues(String relationship) throws Exception {
		mockMvc.perform(registerRequest(ownerUuid, "김아이", "2018-03-02", relationship))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.relationship").value(relationship));
	}

	@ParameterizedTest
	@DisplayName("정해지지 않은 relationship 값은 400 이다")
	@ValueSource(strings = {"MOTHER", "TEACHER", "부모", ""})
	void unknownRelationshipIsRejected(String relationship) throws Exception {
		// 값 목록이 임시 확정이라 나중에 늘어날 수 있다. 늘리는 것은 열거형
		// 한 곳만 고치면 되지만, 지금 아무 문자열이나 받아 두면 나중에 데이터에
		// 무엇이 들어 있는지 알 수 없게 된다.
		mockMvc.perform(registerRequest(ownerUuid, "김아이", "2018-03-02", relationship))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("미래 생년월일은 400 이다")
	void futureBirthDateIsRejected() throws Exception {
		mockMvc.perform(registerRequest(ownerUuid, "김아이", "2999-01-01", "PARENT"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("자녀는 열 명까지 등록할 수 있고 열한 번째는 409 다")
	void childLimitIsTen() throws Exception {
		// "API.md" 3-3 이 확정한 상한(10명)이다.
		for (int index = 1; index <= 10; index++) {
			mockMvc.perform(registerRequest(ownerUuid, "아이" + index, "2018-03-02", "PARENT"))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(registerRequest(ownerUuid, "열한번째", "2018-03-02", "PARENT"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CHILD_LIMIT_EXCEEDED"));
	}

	@Test
	@DisplayName("삭제한 자녀는 상한 계산에서 빠진다")
	void deletedChildDoesNotCountTowardLimit() throws Exception {
		long firstChildId = registerChild(ownerUuid, "첫째");

		for (int index = 2; index <= 10; index++) {
			mockMvc.perform(registerRequest(ownerUuid, "아이" + index, "2018-03-02", "PARENT"))
				.andExpect(status().isCreated());
		}

		// 열 명이 찬 상태에서 하나를 지우면 다시 한 자리가 난다.
		// soft delete 라 행은 남아 있으므로, 세는 쿼리에 조건이 빠지면
		// 지워도 자리가 나지 않아 사용자가 영영 등록하지 못한다.
		mockMvc.perform(delete("/api/v1/children/" + firstChildId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		mockMvc.perform(registerRequest(ownerUuid, "새로운아이", "2018-03-02", "PARENT"))
			.andExpect(status().isCreated());
	}

	// ------------------------------------------------------------------
	// 조회
	// ------------------------------------------------------------------

	@Test
	@DisplayName("목록에는 내 자녀만 나온다")
	void listContainsOnlyOwnChildren() throws Exception {
		registerChild(ownerUuid, "내아이");
		registerChild(strangerUuid, "남의아이");

		mockMvc.perform(get("/api/v1/children").header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].name").value("내아이"));
	}

	@Test
	@DisplayName("삭제한 자녀는 목록에 나오지 않는다")
	void deletedChildIsNotListed() throws Exception {
		long childId = registerChild(ownerUuid, "지울아이");

		mockMvc.perform(delete("/api/v1/children/" + childId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/children").header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("상세 조회는 등록한 값을 그대로 돌려준다")
	void detailReturnsRegisteredValues() throws Exception {
		long childId = registerChild(ownerUuid, "김아이");

		mockMvc.perform(get("/api/v1/children/" + childId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.childId").value(childId))
			.andExpect(jsonPath("$.data.name").value("김아이"));
	}

	// ------------------------------------------------------------------
	// 소유권 검사 — 이 구간의 핵심
	// ------------------------------------------------------------------

	@Test
	@DisplayName("남의 자녀는 조회할 수 없다")
	void strangerCannotReadOthersChild() throws Exception {
		long childId = registerChild(ownerUuid, "내아이");

		// 인증은 통과한 상태다. 로그인만 했다고 아무 자녀나 볼 수 있으면
		// childId 를 1, 2, 3 으로 바꿔가며 남의 아이 정보를 전부 읽을 수 있다.
		mockMvc.perform(get("/api/v1/children/" + childId).header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("남의 자녀는 수정할 수 없다")
	void strangerCannotUpdateOthersChild() throws Exception {
		long childId = registerChild(ownerUuid, "내아이");

		mockMvc.perform(patch("/api/v1/children/" + childId)
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "바꿔치기"}"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("남의 자녀는 삭제할 수 없다")
	void strangerCannotDeleteOthersChild() throws Exception {
		long childId = registerChild(ownerUuid, "내아이");

		mockMvc.perform(delete("/api/v1/children/" + childId).header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("없는 자녀와 남의 자녀는 다른 코드로 구분한다")
	void missingChildAndForbiddenChildAreDistinguished() throws Exception {
		long childId = registerChild(ownerUuid, "내아이");

		// 로그인한 사용자에게는 이 구분이 새어 나가도 문제가 없고,
		// 앱이 "잘못된 요청" 과 "권한 없음" 을 다르게 안내할 수 있어야 한다.
		mockMvc.perform(get("/api/v1/children/99999999").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));

		mockMvc.perform(get("/api/v1/children/" + childId).header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("삭제한 자녀는 주인에게도 404 다")
	void deletedChildIsNotFoundEvenForOwner() throws Exception {
		long childId = registerChild(ownerUuid, "지울아이");

		mockMvc.perform(delete("/api/v1/children/" + childId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		// soft delete 라 행은 남아 있다. 조회에서 거르지 않으면
		// 지운 자녀가 계속 보인다.
		mockMvc.perform(get("/api/v1/children/" + childId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
	}

	@Test
	@DisplayName("인증하지 않으면 자녀 API 를 쓸 수 없다")
	void childApiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/children"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	// ------------------------------------------------------------------
	// 수정 · 삭제
	// ------------------------------------------------------------------

	@Test
	@DisplayName("이름만 보내면 이름만 바뀐다")
	void updateChangesOnlyGivenFields() throws Exception {
		long childId = registerChild(ownerUuid, "옛이름");

		mockMvc.perform(patch("/api/v1/children/" + childId)
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "새이름"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("새이름"))
			// 보내지 않은 값이 null 로 덮이면 안 된다. 이름만 고치려던 사용자가
			// 생년월일을 잃는다.
			.andExpect(jsonPath("$.data.birthDate").value("2018-03-02"));
	}

	@Test
	@DisplayName("자녀를 삭제하면 연결된 기기도 함께 해제된다")
	void deletingChildAlsoReleasesDevices() throws Exception {
		long childId = registerChild(ownerUuid, "기기있는아이");

		jdbcTemplate.update(
			"insert into devices (child_id, device_uid, status, device_access_uuid)"
				+ " values (?, ?, 'ACTIVE', gen_random_uuid())",
			childId, "device-uid-7001");

		mockMvc.perform(delete("/api/v1/children/" + childId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		// "API.md" 6장이 "연결된 기기의 페어링 해제가 함께 일어나야 한다" 고 적었다.
		// 자녀가 사라졌는데 기기가 살아 있으면 그 기기는 주인 없는 상태로
		// 계속 인증에 성공한다.
		Integer liveDeviceCount = jdbcTemplate.queryForObject(
			"select count(*) from devices where child_id = ? and deleted_at is null",
			Integer.class, childId);

		assertThat(liveDeviceCount).isZero();
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

	private org.springframework.test.web.servlet.RequestBuilder registerRequest(
		String accessUuid, String name, String birthDate, String relationship) {

		return post("/api/v1/children")
			.header(APP_HEADER, accessUuid)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"name": "%s", "birthDate": "%s", "relationship": "%s"}"""
				.formatted(name, birthDate, relationship));
	}

	private long registerChild(String accessUuid, String name) throws Exception {
		String body = mockMvc.perform(registerRequest(accessUuid, name, "2018-03-02", "PARENT"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		return root.path("data").path("childId").asLong();
	}

}
