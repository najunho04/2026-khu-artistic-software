package artistic.software.khu.artistic_software_khu.routine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 루틴 API. "API.md" 9장 · 10장.
 *
 * 여기서 확인하려는 것은 세 가지다.
 *
 * 1. 반복 모드 세 가지가 실제로 행을 만들고 캘린더에 나타나는가
 * 2. 수정 · 삭제가 시리즈 전체에 퍼지되 과거는 건드리지 않는가
 * 3. 남의 자녀 루틴에 손댈 수 없는가
 *
 * 특히 세 번째는 경로에 자녀 id 가 없는 엔드포인트까지 확인한다.
 * 빅루틴 id 만 받는 경로에서 검사를 빠뜨리면 id 를 1, 2, 3 으로 바꿔가며
 * 남의 아이 루틴을 전부 고칠 수 있고, 그 실수는 기능이 잘 도는 것처럼
 * 보여 눈으로는 잡히지 않는다.
 *
 * 날짜는 먼 미래를 쓴다. "오늘 이후만 바꾼다" 는 규칙 때문에 오늘에 가까운
 * 날짜를 쓰면 테스트를 언제 돌리느냐에 따라 결과가 달라진다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineApiIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String PASSWORD = "password1234";

	// 오늘보다 확실히 뒤인 날짜. 2099-01-05 는 월요일이다.
	private static final String FUTURE_MONDAY = "2099-01-05";

	private static final String FUTURE_WEEK_END = "2099-01-11";

	private String ownerUuid;

	private String strangerUuid;

	private long childId;

	// 소유권 검사를 찔러 볼 때 쓰는, 다른 보호자의 자녀
	private long strangerChildId;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void prepareGuardianAndChild() throws Exception {
		ownerUuid = signUp("routine-owner@example.com");
		strangerUuid = signUp("routine-stranger@example.com");
		childId = registerChild(ownerUuid);
		strangerChildId = registerChild(strangerUuid);
	}

	// ------------------------------------------------------------------
	// 반복 모드 세 가지
	// ------------------------------------------------------------------

	@Test
	@DisplayName("RANGE 로 만들면 기간 안의 모든 날짜에 생긴다")
	void rangeCreatesEveryDateInPeriod() throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "아침 준비",
			  "startTime": "07:30",
			  "endTime": "08:30",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "smallRoutines": [{"title": "세수하기"}, {"title": "양치하기"}]
			}""".formatted(FUTURE_MONDAY, FUTURE_WEEK_END)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.createdCount").value(7))
			.andExpect(jsonPath("$.data.seriesId").isNotEmpty());
	}

	@Test
	@DisplayName("WEEKLY 로 만들면 지정한 요일에만 생긴다")
	void weeklyCreatesOnlyOnGivenDays() throws Exception {
		// 2099-01-05(월) ~ 2099-01-11(일) 한 주에서 월·수·금은 5일, 7일, 9일이다.
		mockMvc.perform(createRoutine("""
			{
			  "title": "등교 준비",
			  "startTime": "07:00",
			  "endTime": "08:00",
			  "repeatType": "WEEKLY",
			  "startDate": "%s",
			  "endDate": "%s",
			  "repeatDays": ["MON", "WED", "FRI"],
			  "smallRoutines": [{"title": "옷 입기"}]
			}""".formatted(FUTURE_MONDAY, FUTURE_WEEK_END)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.createdCount").value(3))
			.andExpect(jsonPath("$.data.createdDates[0]").value("2099-01-05"))
			.andExpect(jsonPath("$.data.createdDates[1]").value("2099-01-07"))
			.andExpect(jsonPath("$.data.createdDates[2]").value("2099-01-09"));
	}

	@Test
	@DisplayName("DATES 로 만들면 지정한 날짜에만 생긴다")
	void datesCreatesOnlyGivenDates() throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "병원 가기",
			  "startTime": "14:00",
			  "endTime": "15:00",
			  "repeatType": "DATES",
			  "repeatDates": ["2099-01-20", "2099-01-10", "2099-01-15"]
			}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.createdCount").value(3))
			// 보낸 순서와 무관하게 날짜순으로 정리되어야 한다.
			.andExpect(jsonPath("$.data.createdDates[0]").value("2099-01-10"));
	}

	@Test
	@DisplayName("DATES 가 열세 개면 거절한다")
	void datesOverLimitIsRejected() throws Exception {
		StringBuilder dates = new StringBuilder();

		for (int day = 1; day <= 13; day++) {
			dates.append("\"2099-01-%02d\"".formatted(day));
			if (day < 13) {
				dates.append(", ");
			}
		}

		mockMvc.perform(createRoutine("""
			{
			  "title": "너무 많음",
			  "startTime": "09:00",
			  "endTime": "10:00",
			  "repeatType": "DATES",
			  "repeatDates": [%s]
			}""".formatted(dates)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_TOO_MANY_DATES"));
	}

	@Test
	@DisplayName("WEEKLY 인데 요일을 고르지 않으면 거절한다")
	void weeklyWithoutDaysIsRejected() throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "요일없음",
			  "startTime": "09:00",
			  "endTime": "10:00",
			  "repeatType": "WEEKLY",
			  "startDate": "%s",
			  "endDate": "%s",
			  "repeatDays": []
			}""".formatted(FUTURE_MONDAY, FUTURE_WEEK_END)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_REPEAT_RULE"));
	}

	@Test
	@DisplayName("알 수 없는 반복 모드는 거절한다")
	void unknownRepeatTypeIsRejected() throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "이상한모드",
			  "startTime": "09:00",
			  "endTime": "10:00",
			  "repeatType": "EVERY_OTHER_DAY",
			  "startDate": "%s",
			  "endDate": "%s"
			}""".formatted(FUTURE_MONDAY, FUTURE_WEEK_END)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_REPEAT_RULE"));
	}

	@Test
	@DisplayName("종료 시각이 시작 시각보다 빠르면 거절한다")
	void reversedTimeRangeIsRejected() throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "거꾸로",
			  "startTime": "10:00",
			  "endTime": "09:00",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s"
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_INVALID_TIME_RANGE"));
	}

	// ------------------------------------------------------------------
	// 캘린더 조회
	// ------------------------------------------------------------------

	@Test
	@DisplayName("캘린더는 날짜별 상세와 이행률을 함께 준다")
	void calendarReturnsDetailAndCompletionRate() throws Exception {
		createOneDayRoutine("아침 준비", 2);

		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", FUTURE_MONDAY)
				.param("to", FUTURE_MONDAY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].date").value(FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].totalCount").value(2))
			.andExpect(jsonPath("$.data[0].doneCount").value(0))
			// 아무것도 안 한 날은 0 이어야 한다. 100 으로 두면 통계가 부풀려진다.
			.andExpect(jsonPath("$.data[0].completionRate").value(0.0))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("아침 준비"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(2));
	}

	@Test
	@DisplayName("루틴이 없는 기간을 조회하면 빈 배열이다")
	void emptyPeriodReturnsEmptyArray() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, ownerUuid)
				.param("from", "2098-01-01")
				.param("to", "2098-01-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	// ------------------------------------------------------------------
	// 수정 · 삭제 전파
	// ------------------------------------------------------------------

	@Test
	@DisplayName("수정은 기본으로 같은 시리즈 전체에 퍼진다")
	void updateSpreadsToWholeSeriesByDefault() throws Exception {
		createWeekRoutine("아침 준비");

		long firstId = firstBigRoutineIdOn(FUTURE_MONDAY);

		mockMvc.perform(patch("/api/v1/big-routines/" + firstId)
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "등교 준비"}"""))
			.andExpect(status().isOk());

		// 반복으로 만든 루틴은 사용자 눈에 하나다. 월요일만 바뀌고
		// 나머지가 그대로면 이상하다.
		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_WEEK_END))
			.andExpect(jsonPath("$.data[6].bigRoutines[0].title").value("등교 준비"));
	}

	@Test
	@DisplayName("scope=single 이면 그 날짜만 바뀐다")
	void singleScopeChangesOnlyOneDate() throws Exception {
		createWeekRoutine("아침 준비");

		long firstId = firstBigRoutineIdOn(FUTURE_MONDAY);

		mockMvc.perform(patch("/api/v1/big-routines/" + firstId)
				.header(APP_HEADER, ownerUuid)
				.param("scope", "single")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "월요일만"}"""))
			.andExpect(status().isOk());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_WEEK_END))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("월요일만"))
			.andExpect(jsonPath("$.data[6].bigRoutines[0].title").value("아침 준비"));
	}

	@Test
	@DisplayName("삭제는 기본으로 그 날짜만 지운다")
	void deleteRemovesOnlyOneDateByDefault() throws Exception {
		createWeekRoutine("아침 준비");

		long firstId = firstBigRoutineIdOn(FUTURE_MONDAY);

		// 수정과 기본값이 반대인 것은 의도한 것이다. 수정은 되돌릴 수 있지만
		// 삭제는 어렵다.
		mockMvc.perform(delete("/api/v1/big-routines/" + firstId).header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_WEEK_END))
			.andExpect(jsonPath("$.data.length()").value(6));
	}

	@Test
	@DisplayName("scope=series 로 삭제하면 시리즈 전체가 지워진다")
	void seriesScopeDeleteRemovesWholeSeries() throws Exception {
		createWeekRoutine("아침 준비");

		long firstId = firstBigRoutineIdOn(FUTURE_MONDAY);

		mockMvc.perform(delete("/api/v1/big-routines/" + firstId)
				.header(APP_HEADER, ownerUuid)
				.param("scope", "series"))
			.andExpect(status().isNoContent());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_WEEK_END))
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	// ------------------------------------------------------------------
	// 스몰루틴
	// ------------------------------------------------------------------

	@Test
	@DisplayName("스몰루틴을 더하면 시리즈 전체에 더해진다")
	void addingSmallRoutineSpreadsToSeries() throws Exception {
		createWeekRoutine("아침 준비");

		long firstId = firstBigRoutineIdOn(FUTURE_MONDAY);

		mockMvc.perform(post("/api/v1/big-routines/" + firstId + "/small-routines")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "옷 입기"}"""))
			.andExpect(status().isCreated());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_WEEK_END))
			.andExpect(jsonPath("$.data[0].totalCount").value(2))
			.andExpect(jsonPath("$.data[6].totalCount").value(2));
	}

	@Test
	@DisplayName("순서 변경 요청에 하나라도 빠지면 거절한다")
	void reorderWithMissingIdIsRejected() throws Exception {
		long bigRoutineId = createOneDayRoutine("아침 준비", 3);

		long firstSmallRoutineId = firstSmallRoutineIdOn(FUTURE_MONDAY);

		// 일부만 보내면 나머지의 순서를 서버가 짐작해야 하는데,
		// 그 짐작이 사용자가 화면에서 본 것과 다를 수 있다.
		mockMvc.perform(put("/api/v1/big-routines/" + bigRoutineId + "/small-routines/order")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"smallRoutineIds": [%d]}""".formatted(firstSmallRoutineId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("ROUTINE_ORDER_MISMATCH"));
	}

	// ------------------------------------------------------------------
	// 소유권 — 이 구간의 핵심
	// ------------------------------------------------------------------

	@Test
	@DisplayName("남의 자녀에게 루틴을 만들 수 없다")
	void strangerCannotCreateRoutineForOthersChild() throws Exception {
		mockMvc.perform(post("/api/v1/children/" + childId + "/big-routines")
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "몰래넣기",
					  "startTime": "09:00",
					  "endTime": "10:00",
					  "repeatType": "RANGE",
					  "startDate": "%s",
					  "endDate": "%s"
					}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("남의 자녀 캘린더는 볼 수 없다")
	void strangerCannotReadOthersCalendar() throws Exception {
		mockMvc.perform(get("/api/v1/children/" + childId + "/calendar")
				.header(APP_HEADER, strangerUuid)
				.param("from", FUTURE_MONDAY)
				.param("to", FUTURE_WEEK_END))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("경로에 자녀 id 가 없어도 남의 루틴은 고칠 수 없다")
	void strangerCannotUpdateOthersRoutineById() throws Exception {
		long bigRoutineId = createOneDayRoutine("아침 준비", 1);

		// 이 검사가 없으면 bigRoutineId 를 1, 2, 3 으로 바꿔가며
		// 남의 아이 루틴을 전부 고칠 수 있다.
		mockMvc.perform(patch("/api/v1/big-routines/" + bigRoutineId)
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "바꿔치기"}"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("경로에 자녀 id 가 없어도 남의 루틴은 지울 수 없다")
	void strangerCannotDeleteOthersRoutineById() throws Exception {
		long bigRoutineId = createOneDayRoutine("아침 준비", 1);

		mockMvc.perform(delete("/api/v1/big-routines/" + bigRoutineId)
				.header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("없는 빅루틴은 404 다")
	void unknownBigRoutineIsNotFound() throws Exception {
		mockMvc.perform(patch("/api/v1/big-routines/99999999")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "없는것"}"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("BIG_ROUTINE_NOT_FOUND"));
	}

	// ------------------------------------------------------------------
	// 템플릿 (저장해둔 양식)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("양식을 저장하면 목록에 나타난다")
	void savedTemplateAppearsInList() throws Exception {
		saveTemplate("저녁 루틴");

		mockMvc.perform(get("/api/v1/children/" + childId + "/routine-templates")
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].title").value("저녁 루틴"))
			.andExpect(jsonPath("$.data[0].smallRoutines.length()").value(2));
	}

	@Test
	@DisplayName("양식을 꺼내 만들면 제목·시각·할 일이 모두 복사된다")
	void creatingFromTemplateCopiesEveryField() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		// 양식만 지정하고 나머지는 아무것도 보내지 않는다. 양식을 꺼내 쓰는
		// 목적이 "저장해둔 값을 그대로 쓰는 것" 이므로 이것이 기본 사용법이다.
		mockMvc.perform(createRoutine("""
			{
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "templateId": %d
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isCreated());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("저녁 루틴"))
			// 시각을 안 가져오면 루틴이 몇 시에 하는 것인지 알 수 없게 된다.
			.andExpect(jsonPath("$.data[0].bigRoutines[0].startTime").value("19:00"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].endTime").value("20:00"))
			// 할 일이 안 딸려오면 빈 루틴이 만들어진다. 양식을 저장해 둔
			// 이유가 거의 사라지는 셈이다.
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(2))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines[0].title").value("숙제하기"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines[1].title").value("책 읽기"));
	}

	@Test
	@DisplayName("양식과 요청에 값이 둘 다 있으면 요청이 이긴다")
	void requestValuesWinOverTemplateValues() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		// 앱이 양식을 불러와 화면을 채우고, 사용자가 값을 고친 뒤 저장하는
		// 흐름이다. 고친 값이 무시되면 사용자는 왜 안 바뀌는지 알 수 없다.
		mockMvc.perform(createRoutine("""
			{
			  "title": "밤 루틴",
			  "startTime": "20:00",
			  "endTime": "21:00",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "smallRoutines": [{"title": "일기 쓰기"}],
			  "templateId": %d
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isCreated());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("밤 루틴"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].startTime").value("20:00"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(1))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines[0].title").value("일기 쓰기"));
	}

	@Test
	@DisplayName("일부만 보내면 나머지는 양식 값으로 채운다")
	void missingFieldsAreFilledFromTemplate() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		// 제목만 바꾸고 시각과 할 일은 양식 그대로 쓰는 경우다.
		mockMvc.perform(createRoutine("""
			{
			  "title": "밤 루틴",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "templateId": %d
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isCreated());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("밤 루틴"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].startTime").value("19:00"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(2));
	}

	@Test
	@DisplayName("남의 양식으로는 루틴을 만들 수 없다")
	void strangerTemplateCannotBeUsed() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		// 양식을 꺼내는 것도 소유권 검사를 거쳐야 한다. 검사가 없으면
		// templateId 를 1, 2, 3 으로 바꿔가며 남이 저장해둔 양식의 내용을
		// 자기 루틴으로 만들어 읽을 수 있다.
		mockMvc.perform(post("/api/v1/children/" + strangerChildId + "/big-routines")
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "repeatType": "RANGE",
					  "startDate": "%s",
					  "endDate": "%s",
					  "templateId": %d
					}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("양식을 수정해도 이미 만든 빅루틴은 그대로다")
	void updatingTemplateDoesNotAffectExistingRoutines() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		mockMvc.perform(createRoutine("""
			{
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "templateId": %d
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/routine-templates/" + templateId)
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "바뀐 양식"}"""))
			.andExpect(status().isOk());

		// 꺼내 쓰는 순간 값이 복사되고 둘의 관계는 거기서 끝난다.
		// 워드의 서식 파일을 고쳐도 이미 만든 문서는 그대로인 것과 같다.
		//
		// 제목만 확인하지 않고 시각과 할 일까지 본다. 제목만 보면 "복사는
		// 제목만 되고 나머지는 아예 안 되는" 상태에서도 초록불이 뜬다.
		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("저녁 루틴"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].startTime").value("19:00"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(2));
	}

	@Test
	@DisplayName("양식을 지워도 그 양식으로 만든 빅루틴은 남는다")
	void deletingTemplateKeepsCreatedRoutines() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		mockMvc.perform(createRoutine("""
			{
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "templateId": %d
			}""".formatted(FUTURE_MONDAY, FUTURE_MONDAY, templateId)))
			.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/routine-templates/" + templateId)
				.header(APP_HEADER, ownerUuid))
			.andExpect(status().isNoContent());

		mockMvc.perform(calendarRequest(FUTURE_MONDAY, FUTURE_MONDAY))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].title").value("저녁 루틴"))
			.andExpect(jsonPath("$.data[0].bigRoutines[0].smallRoutines.length()").value(2));

		mockMvc.perform(get("/api/v1/children/" + childId + "/routine-templates")
				.header(APP_HEADER, ownerUuid))
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("남의 자녀 양식은 볼 수 없다")
	void strangerCannotReadOthersTemplates() throws Exception {
		saveTemplate("저녁 루틴");

		mockMvc.perform(get("/api/v1/children/" + childId + "/routine-templates")
				.header(APP_HEADER, strangerUuid))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHILD_FORBIDDEN"));
	}

	@Test
	@DisplayName("경로에 자녀 id 가 없어도 남의 양식은 고칠 수 없다")
	void strangerCannotUpdateOthersTemplateById() throws Exception {
		long templateId = saveTemplate("저녁 루틴");

		mockMvc.perform(patch("/api/v1/routine-templates/" + templateId)
				.header(APP_HEADER, strangerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "바꿔치기"}"""))
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

	private RequestBuilder createRoutine(String body) {
		return post("/api/v1/children/" + childId + "/big-routines")
			.header(APP_HEADER, ownerUuid)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private RequestBuilder calendarRequest(String from, String to) {
		return get("/api/v1/children/" + childId + "/calendar")
			.header(APP_HEADER, ownerUuid)
			.param("from", from)
			.param("to", to);
	}

	/** 하루짜리 루틴을 만들고 그 빅루틴 id 를 돌려준다. */
	private long createOneDayRoutine(String title, int smallRoutineCount) throws Exception {
		StringBuilder smallRoutines = new StringBuilder();

		for (int index = 1; index <= smallRoutineCount; index++) {
			smallRoutines.append("{\"title\": \"할일%d\"}".formatted(index));
			if (index < smallRoutineCount) {
				smallRoutines.append(", ");
			}
		}

		mockMvc.perform(createRoutine("""
			{
			  "title": "%s",
			  "startTime": "07:30",
			  "endTime": "08:30",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "smallRoutines": [%s]
			}""".formatted(title, FUTURE_MONDAY, FUTURE_MONDAY, smallRoutines)))
			.andExpect(status().isCreated());

		return firstBigRoutineIdOn(FUTURE_MONDAY);
	}

	private void createWeekRoutine(String title) throws Exception {
		mockMvc.perform(createRoutine("""
			{
			  "title": "%s",
			  "startTime": "07:30",
			  "endTime": "08:30",
			  "repeatType": "RANGE",
			  "startDate": "%s",
			  "endDate": "%s",
			  "smallRoutines": [{"title": "세수하기"}]
			}""".formatted(title, FUTURE_MONDAY, FUTURE_WEEK_END)))
			.andExpect(status().isCreated());
	}

	private long firstBigRoutineIdOn(String date) throws Exception {
		String body = mockMvc.perform(calendarRequest(date, date))
			.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		return root.path("data").get(0).path("bigRoutines").get(0).path("bigRoutineId").asLong();
	}

	private long firstSmallRoutineIdOn(String date) throws Exception {
		String body = mockMvc.perform(calendarRequest(date, date))
			.andReturn().getResponse().getContentAsString();

		JsonNode root = objectMapper.readTree(body);
		return root.path("data").get(0).path("bigRoutines").get(0)
			.path("smallRoutines").get(0).path("smallRoutineId").asLong();
	}

	private long saveTemplate(String title) throws Exception {
		String body = mockMvc.perform(post("/api/v1/children/" + childId + "/routine-templates")
				.header(APP_HEADER, ownerUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "%s",
					  "startTime": "19:00",
					  "endTime": "20:00",
					  "smallRoutines": [{"title": "숙제하기"}, {"title": "책 읽기"}]
					}""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		return objectMapper.readTree(body).path("data").path("templateId").asLong();
	}

}
