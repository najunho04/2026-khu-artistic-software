package artistic.software.khu.artistic_software_khu.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
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
 * 캐릭터. "API.md" 12장, 로드맵 6-2.
 *
 * 조회 두 개와 기기 동기화 시점의 획득 · 진화를 함께 확인한다.
 *
 * 규칙은 이렇다. 동기화로 "새로 완료된" 할 일 1개당 exp 가 1 오르고,
 * level 은 exp / 10 + 1 이며 최대 3 이다. exp 는 30 에서 멈추고, 다 큰 뒤에
 * 경험치가 더 들어오면 도감의 code 오름차순으로 다음 캐릭터를 받는다.
 *
 * 도감("characters" 테이블) 은 비어 있는 것이 기본이다. 캐릭터 이름과 이미지가
 * 아직 없어 마이그레이션으로 넣지 않기로 했다. 그래서 이 테스트가 도감을
 * 직접 채워 넣는다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CharacterApiIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String DEVICE_HEADER = "X-Device-Uuid";

	private static final String PASSWORD = "password1234";

	// 오늘에 가까운 날짜를 쓰면 테스트를 언제 돌리느냐에 따라 결과가 달라진다.
	private static final String ROUTINE_DATE = "2099-01-05";

	// 한 마리를 다 키우는 데 드는 미션 수. "API.md" 12-1.
	private static final int MISSIONS_PER_CHARACTER = 30;

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
	void prepareOnboardedChild() throws Exception {
		ownerUuid = signUp("character-owner@example.com");
		childId = registerChild(ownerUuid);
		deviceAccessUuid = pairAndClaim("device-uid-character-1");
	}

	// ------------------------------------------------------------------
	// 조회
	// ------------------------------------------------------------------

	@Test
	@DisplayName("도감은 S3 키를 그대로 주고 획득 가중치는 응답에 없다")
	void charactersExposeAssetKeysButNeverWeight() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");

		String body = mockMvc.perform(get("/api/v1/characters").header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].characterId").isNumber())
			.andExpect(jsonPath("$.data[0].code").value("PENGUIN_01"))
			.andExpect(jsonPath("$.data[0].name").value("펭구"))
			.andExpect(jsonPath("$.data[0].rarity").value("COMMON"))
			// URL 조립은 클라이언트가 한다. 서버가 조립하면 저장 위치가 바뀔 때
			// 서버와 앱을 함께 고쳐야 한다.
			.andExpect(jsonPath("$.data[0].assets.thumbnail")
				.value("characters/penguin_01/thumb.png"))
			.andExpect(jsonPath("$.data[0].assets.idle").value("characters/penguin_01/idle.png"))
			.andReturn().getResponse().getContentAsString();

		// weight 는 서버 내부용이다. 지금은 쓰지도 않지만, 응답에 실리면
		// 앱이 그 값을 보고 무언가를 계산하기 시작할 수 있다.
		assertThat(body).doesNotContain("weight");
	}

	@Test
	@DisplayName("도감이 비어 있으면 빈 배열이다")
	void emptyCatalogueReturnsEmptyArray() throws Exception {
		// 캐릭터 디자인이 나오기 전까지 실제 서버의 도감은 비어 있다.
		// 그 상태가 오류가 아니라 정상이어야 한다.
		mockMvc.perform(get("/api/v1/characters").header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("보유 캐릭터 응답에 일곱 개 필드가 담긴다")
	void ownedCharacterResponseHasSevenFields() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");
		completeMissions(1);

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].childCharacterId").isNumber())
			.andExpect(jsonPath("$.data[0].characterId").isNumber())
			.andExpect(jsonPath("$.data[0].code").value("PENGUIN_01"))
			.andExpect(jsonPath("$.data[0].name").value("펭구"))
			.andExpect(jsonPath("$.data[0].level").value(1))
			.andExpect(jsonPath("$.data[0].exp").value(1))
			.andExpect(jsonPath("$.data[0].acquiredAt").isNotEmpty());
	}

	@Test
	@DisplayName("한 마리도 없으면 보유 캐릭터는 빈 배열이다")
	void childWithoutCharactersReturnsEmptyArray() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("남의 자녀 보유 캐릭터를 보면 403 이다")
	void otherGuardianCannotSeeCharacters() throws Exception {
		String otherUuid = signUp("character-other@example.com");

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, otherUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("없는 자녀의 보유 캐릭터를 보면 404 다")
	void missingChildReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/children/999999/characters").header(APP_HEADER, ownerUuid))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
	}

	// ------------------------------------------------------------------
	// 획득 · 진화
	// ------------------------------------------------------------------

	@Test
	@DisplayName("첫 동기화에서 도감 첫 캐릭터를 받는다")
	void firstSyncGrantsFirstCharacterInCatalogue() throws Exception {
		// 순서가 code 오름차순이라 같은 입력이면 항상 같은 캐릭터가 나온다.
		insertCharacter("BEAR_02", "곰돌");
		insertCharacter("ALPACA_01", "알파카");

		completeMissions(1);

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].code").value("ALPACA_01"))
			.andExpect(jsonPath("$.data[0].level").value(1))
			.andExpect(jsonPath("$.data[0].exp").value(1));
	}

	@Test
	@DisplayName("완료 10개면 진화하고 20개면 최대 레벨이 된다")
	void experienceRaisesLevelEveryTenMissions() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");

		completeMissions(10);
		assertThat(levelOfFirstCharacter()).isEqualTo(2);

		completeMissions(20);
		assertThat(levelOfFirstCharacter()).isEqualTo(3);
	}

	@Test
	@DisplayName("서른 개를 채운 뒤 완료가 더 들어오면 다음 캐릭터를 받는다")
	void nextCharacterArrivesAfterCurrentOneIsFullyGrown() throws Exception {
		insertCharacter("ALPACA_01", "알파카");
		insertCharacter("BEAR_02", "곰돌");

		completeMissions(MISSIONS_PER_CHARACTER + 1);

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(2))
			// 먼저 받은 캐릭터가 먼저 나온다. 그리고 30 에서 멈춘다.
			.andExpect(jsonPath("$.data[0].code").value("ALPACA_01"))
			.andExpect(jsonPath("$.data[0].level").value(3))
			.andExpect(jsonPath("$.data[0].exp").value(MISSIONS_PER_CHARACTER))
			.andExpect(jsonPath("$.data[1].code").value("BEAR_02"))
			.andExpect(jsonPath("$.data[1].level").value(1))
			.andExpect(jsonPath("$.data[1].exp").value(1));
	}

	@Test
	@DisplayName("같은 완료 기록을 다시 보내도 경험치가 오르지 않는다")
	void resendingTheSameCompletionsGrantsNoExperience() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");

		List<Long> missionIds = createMissions(3);
		syncCompletions(missionIds, "DONE");

		assertThat(expOfFirstCharacter()).isEqualTo(3);

		// 기기는 네트워크가 끊기면 같은 요청을 다시 보내는 것 외에 할 수 있는
		// 일이 없다. 재전송할 때마다 캐릭터가 자라면 아이의 성장이 네트워크
		// 상태에 좌우된다.
		syncCompletions(missionIds, "DONE");

		assertThat(expOfFirstCharacter()).isEqualTo(3);
	}

	@Test
	@DisplayName("완료를 취소해도 경험치는 줄지 않는다")
	void undoingCompletionNeverTakesExperienceAway() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");

		List<Long> missionIds = createMissions(2);
		syncCompletions(missionIds, "DONE");

		assertThat(expOfFirstCharacter()).isEqualTo(2);

		// 아이가 실수로 눌렀다가 취소하는 일은 실제로 일어난다. 그때 캐릭터가
		// 뒷걸음질하면 아이가 이유를 알 수 없다.
		syncCompletions(missionIds, "PENDING");

		assertThat(expOfFirstCharacter()).isEqualTo(2);
	}

	@Test
	@DisplayName("도감이 비어 있으면 동기화는 성공하고 캐릭터는 생기지 않는다")
	void syncSucceedsWithEmptyCatalogue() throws Exception {
		// 실제 서버가 지금 이 상태다. 여기서 오류가 나면 도감이 채워지기 전까지
		// 기기가 아예 동기화를 못 한다.
		completeMissions(5);

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("도감을 다 모은 뒤에는 새 캐릭터가 생기지 않는다")
	void nothingHappensWhenCatalogueIsExhausted() throws Exception {
		insertCharacter("PENGUIN_01", "펭구");

		completeMissions(MISSIONS_PER_CHARACTER + 5);

		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(1))
			// 남은 경험치는 버린다. 다 자란 캐릭터에 계속 쌓으면 30 이라는
			// 상한이 의미를 잃는다.
			.andExpect(jsonPath("$.data[0].exp").value(MISSIONS_PER_CHARACTER));
	}

	@Test
	@DisplayName("감춘 캐릭터는 도감에도 없고 지급되지도 않는다")
	void inactiveCharacterIsNeitherListedNorGranted() throws Exception {
		insertInactiveCharacter("HIDDEN_00", "숨은캐릭터");
		insertCharacter("PENGUIN_01", "펭구");

		mockMvc.perform(get("/api/v1/characters").header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].code").value("PENGUIN_01"));

		completeMissions(1);

		// code 오름차순으로는 HIDDEN_00 이 먼저다. is_active 를 보지 않으면
		// 감춘 캐릭터가 지급된다.
		mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data[0].code").value("PENGUIN_01"));
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private void insertCharacter(String code, String name) {
		jdbcTemplate.update(
			"insert into characters (code, name, rarity, weight, assets, is_active)"
				+ " values (?, ?, 'COMMON', 10, ?::jsonb, true)",
			code, name, assetsJson(code));
	}

	private void insertInactiveCharacter(String code, String name) {
		jdbcTemplate.update(
			"insert into characters (code, name, rarity, weight, assets, is_active)"
				+ " values (?, ?, 'COMMON', 10, ?::jsonb, false)",
			code, name, assetsJson(code));
	}

	/** 문서의 예시와 같은 모양으로 만든다. 값은 S3 키 문자열이다. */
	private String assetsJson(String code) {
		String directory = "characters/" + code.toLowerCase();

		return """
			{"thumbnail": "%s/thumb.png", "idle": "%s/idle.png"}"""
			.formatted(directory, directory);
	}

	/** 할 일을 만들고 그만큼 완료로 올린다. 캐릭터를 키우는 가장 짧은 경로다. */
	private void completeMissions(int missionCount) throws Exception {
		syncCompletions(createMissions(missionCount), "DONE");
	}

	/** 할 일 여러 개를 만들고 id 목록을 돌려준다. */
	private List<Long> createMissions(int missionCount) throws Exception {
		StringBuilder smallRoutines = new StringBuilder();

		for (int index = 0; index < missionCount; index++) {
			smallRoutines.append("{\"title\": \"할일%d\"}".formatted(index));
			if (index < missionCount - 1) {
				smallRoutines.append(", ");
			}
		}

		String createBody = mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
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
					  "smallRoutines": [%s]
					}""".formatted(ROUTINE_DATE, ROUTINE_DATE, smallRoutines)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		// 생성 응답에는 할 일 id 가 없다("API.md" 9장). 캘린더로 읽어 온다.
		String seriesId = objectMapper.readTree(createBody).path("data").path("seriesId").asText();

		String calendarBody = mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", ROUTINE_DATE)
				.param("to", ROUTINE_DATE))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		List<Long> missionIds = new ArrayList<>();

		// 한 테스트에서 두 번 만들면 같은 날짜에 빅루틴이 두 개가 된다.
		// 방금 만든 것만 골라야 하므로 seriesId 로 거른다.
		for (JsonNode day : objectMapper.readTree(calendarBody).path("data")) {
			for (JsonNode bigRoutine : day.path("bigRoutines")) {
				if (!seriesId.equals(bigRoutine.path("seriesId").asText())) {
					continue;
				}

				for (JsonNode smallRoutine : bigRoutine.path("smallRoutines")) {
					missionIds.add(smallRoutine.path("smallRoutineId").asLong());
				}
			}
		}

		return missionIds;
	}

	private void syncCompletions(List<Long> missionIds, String status) throws Exception {
		StringBuilder completions = new StringBuilder();

		for (int index = 0; index < missionIds.size(); index++) {
			completions.append(
				"""
					{"smallRoutineId": %d, "status": "%s", "completedAt": "2099-01-05T07:42:00Z"}"""
					.formatted(missionIds.get(index), status));

			if (index < missionIds.size() - 1) {
				completions.append(", ");
			}
		}

		mockMvc.perform(post("/device-api/v1/sync")
				.header(DEVICE_HEADER, deviceAccessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"battery": 70, "firmware": "1.0.3", "completions": [%s], "dates": ["%s"]}"""
					.formatted(completions, ROUTINE_DATE)))
			.andExpect(status().isOk());

		// 하이버네이트가 들고 있는 변경을 내려보낸다. 아래 확인이 새 요청으로
		// 이어지므로 남아 있으면 직전 성장 결과가 보이지 않는다.
		entityManager.flush();
		entityManager.clear();
	}

	private int levelOfFirstCharacter() throws Exception {
		return firstCharacterField("level");
	}

	private int expOfFirstCharacter() throws Exception {
		return firstCharacterField("exp");
	}

	private int firstCharacterField(String fieldName) throws Exception {
		String body = mockMvc.perform(get("/api/v1/children/" + childId + "/characters")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").get(0).path(fieldName).asInt();
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

}
