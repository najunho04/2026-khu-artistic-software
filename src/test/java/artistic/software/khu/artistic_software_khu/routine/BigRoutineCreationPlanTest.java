package artistic.software.khu.artistic_software_khu.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 빅루틴 생성 요청을 "실제로 만들 행들" 로 펼치는 규칙을 확인한다.
 *
 * DB 없이 검증하는 이유는 여기서 정하는 것이 저장 방법이 아니라 규칙이기 때문이다.
 * 며칠치를 만들지, 같은 미션으로 묶을지, 어떤 요청을 거절할지가 전부 여기서 끝난다.
 *
 * 이 조각을 따로 두는 가장 큰 이유는 seriesId 다. "ROADMAP.md" 3-1 이 경고하듯
 * 반복문 안에서 UUID 를 만들면 날짜마다 다른 값이 붙어 시리즈가 쪼개지고,
 * 그러면 미션별 이행률 통계가 조용히 무너진다. 오류가 나지 않아 알아차리기도 어렵다.
 * 값을 한 번만 만들어 계획 전체가 공유하게 하면 그 실수를 할 자리가 사라진다.
 */
class BigRoutineCreationPlanTest {

	// "API.md" 15장 #7 의 기간 상한이 아직 미확정이라 설정값으로 주입받는다.
	// "ROADMAP.md" 3-1 이 "확정 전까지 상한값은 설정값으로 빼고" 라고 지시한 대로다.
	private static final int MAX_DATE_RANGE_LENGTH = 31;

	// "API.md" 9장에서 확정한 DATES 모드의 날짜 개수 상한.
	private static final int MAX_DATE_COUNT = 12;

	private static final LocalTime SEVEN_THIRTY = LocalTime.of(7, 30);

	private static final LocalTime EIGHT_THIRTY = LocalTime.of(8, 30);

	@Test
	@DisplayName("endDate 가 없으면 startDate 하루만 만든다")
	void createsSingleDateWhenEndDateIsAbsent() {
		BigRoutineCreationPlan plan = plan(LocalDate.of(2026, 9, 1), null);

		assertThat(plan.routineDates()).containsExactly(LocalDate.of(2026, 9, 1));
		assertThat(plan.createdCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("기간을 주면 시작일과 종료일을 포함해 날짜 수만큼 만든다")
	void createsEveryDateInRangeIncludingBothEnds() {
		BigRoutineCreationPlan plan =
			plan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

		assertThat(plan.createdCount()).isEqualTo(7);
		assertThat(plan.routineDates())
			.startsWith(LocalDate.of(2026, 9, 1))
			.endsWith(LocalDate.of(2026, 9, 7));
	}

	@Test
	@DisplayName("기간으로 만든 날짜들은 모두 같은 seriesId 를 공유한다")
	void everyDateInRangeSharesOneSeriesId() {
		// 시리즈가 쪼개지면 "양치하기 미션의 이행률" 같은 집계가 날짜별로 흩어진다.
		// 계획이 seriesId 를 하나만 들고 있으므로 쪼개질 자리가 없다.
		BigRoutineCreationPlan plan =
			plan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

		assertThat(plan.seriesId()).isNotNull();
		assertThat(plan.routineDates()).hasSize(7);
	}

	@Test
	@DisplayName("생성할 때마다 서로 다른 seriesId 를 받는다")
	void separateCreationsGetDifferentSeriesIds() {
		// 같은 내용으로 두 번 만들어도 서로 다른 미션이다. 같은 값이 나오면
		// 관계없는 루틴들이 한 시리즈로 묶여 통계가 섞인다.
		BigRoutineCreationPlan first = plan(LocalDate.of(2026, 9, 1), null);
		BigRoutineCreationPlan second = plan(LocalDate.of(2026, 9, 1), null);

		assertThat(first.seriesId()).isNotEqualTo(second.seriesId());
	}

	@Test
	@DisplayName("종료 시각이 시작 시각보다 빠르면 거절한다")
	void rejectsEndTimeEarlierThanStartTime() {
		assertThatThrownBy(() -> BigRoutineCreationPlan.ofRange(
			"아침 준비", EIGHT_THIRTY, SEVEN_THIRTY,
			LocalDate.of(2026, 9, 1), null, MAX_DATE_RANGE_LENGTH))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_TIME_RANGE);
	}

	@Test
	@DisplayName("종료일이 시작일보다 빠르면 거절한다")
	void rejectsEndDateEarlierThanStartDate() {
		assertThatThrownBy(() -> plan(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 1)))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_DATE_RANGE);
	}

	@Test
	@DisplayName("기간 상한을 넘으면 거절한다")
	void rejectsDateRangeLongerThanLimit() {
		// 상한이 없으면 한 번의 요청으로 몇 년치 행을 만들 수 있다.
		// 상한값 자체는 아직 미확정이라 설정값으로 주입한다.
		LocalDate start = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> plan(start, start.plusDays(MAX_DATE_RANGE_LENGTH)))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_DATE_RANGE_TOO_LONG);
	}

	@Test
	@DisplayName("상한과 정확히 같은 길이는 통과한다")
	void acceptsDateRangeExactlyAtLimit() {
		// 경계에서 하루 차이로 갈리는 실수를 막는다.
		LocalDate start = LocalDate.of(2026, 9, 1);

		BigRoutineCreationPlan plan = plan(start, start.plusDays(MAX_DATE_RANGE_LENGTH - 1));

		assertThat(plan.createdCount()).isEqualTo(MAX_DATE_RANGE_LENGTH);
	}

	@Test
	@DisplayName("시작 시각과 종료 시각이 같으면 거절한다")
	void rejectsEndTimeEqualToStartTime() {
		// 길이가 0 인 루틴은 의미가 없다. "API.md" 가 "startTime 보다 뒤여야 함" 이라고
		// 적었으므로 같은 값은 뒤가 아니다.
		assertThatThrownBy(() -> BigRoutineCreationPlan.ofRange(
			"아침 준비", SEVEN_THIRTY, SEVEN_THIRTY,
			LocalDate.of(2026, 9, 1), null, MAX_DATE_RANGE_LENGTH))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_TIME_RANGE);
	}

	// ------------------------------------------------------------------
	// WEEKLY — 기간 안에서 지정한 요일마다
	// ------------------------------------------------------------------

	@Test
	@DisplayName("WEEKLY 는 기간 안의 지정한 요일에만 만든다")
	void weeklyCreatesOnlyOnGivenDaysOfWeek() {
		// 2026-09-01 은 화요일이다. 9월 1일부터 9월 7일까지 중
		// 월요일은 7일, 수요일은 2일, 금요일은 4일이다.
		BigRoutineCreationPlan plan = weeklyPlan(
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
			Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

		assertThat(plan.routineDates()).containsExactly(
			LocalDate.of(2026, 9, 2),
			LocalDate.of(2026, 9, 4),
			LocalDate.of(2026, 9, 7));
	}

	@Test
	@DisplayName("WEEKLY 로 만든 날짜도 모두 같은 seriesId 를 공유한다")
	void weeklyDatesShareOneSeriesId() {
		BigRoutineCreationPlan plan = weeklyPlan(
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
			Set.of(DayOfWeek.MONDAY));

		assertThat(plan.seriesId()).isNotNull();
		assertThat(plan.createdCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("기간 안에 해당 요일이 하나도 없으면 오류가 아니라 0개다")
	void weeklyWithNoMatchingDayCreatesNothing() {
		// 사용자가 "다음 주 월요일부터" 를 기대하고 짧은 기간을 골랐을 뿐이다.
		// 오류로 막으면 왜 안 되는지 알 수 없다. 0개를 돌려주고 앱이
		// "만들어진 루틴이 없습니다" 를 보여주는 편이 낫다.
		BigRoutineCreationPlan plan = weeklyPlan(
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
			Set.of(DayOfWeek.SUNDAY));

		assertThat(plan.routineDates()).isEmpty();
		assertThat(plan.createdCount()).isZero();
	}

	@Test
	@DisplayName("요일 목록이 비어 있으면 거절한다")
	void weeklyWithEmptyDaysIsRejected() {
		// 위의 "0개" 와 다르다. 저것은 고른 요일이 기간에 없는 것이고
		// 이것은 요일을 아예 고르지 않은 것이라 요청 자체가 성립하지 않는다.
		assertThatThrownBy(() -> weeklyPlan(
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Set.of()))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
	}

	@Test
	@DisplayName("WEEKLY 도 종료일이 시작일보다 빠르면 거절한다")
	void weeklyRejectsReversedDateRange() {
		assertThatThrownBy(() -> weeklyPlan(
			LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 1),
			Set.of(DayOfWeek.MONDAY)))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_DATE_RANGE);
	}

	@Test
	@DisplayName("WEEKLY 는 훑는 기간이 상한을 넘으면 거절한다")
	void weeklyRejectsScanRangeLongerThanLimit() {
		// 만들어지는 행이 적더라도 훑어야 하는 날짜는 기간 전체다.
		// 상한을 기간에 걸지 않으면 "매주 월요일, 10년치" 같은 요청이
		// 3650일을 훑게 된다.
		LocalDate start = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> weeklyPlan(
			start, start.plusDays(MAX_DATE_RANGE_LENGTH), Set.of(DayOfWeek.MONDAY)))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_DATE_RANGE_TOO_LONG);
	}

	// ------------------------------------------------------------------
	// DATES — 지정한 날짜들만
	// ------------------------------------------------------------------

	@Test
	@DisplayName("DATES 는 지정한 날짜에만 만든다")
	void datesCreatesOnlyGivenDates() {
		BigRoutineCreationPlan plan = datesPlan(List.of(
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 12),
			LocalDate.of(2026, 9, 17)));

		assertThat(plan.routineDates()).containsExactly(
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 12),
			LocalDate.of(2026, 9, 17));
	}

	@Test
	@DisplayName("DATES 는 순서가 뒤섞여 와도 날짜순으로 정리한다")
	void datesAreSortedRegardlessOfInputOrder() {
		// 앱이 어떤 순서로 보내든 결과가 같아야 한다. 정리하지 않으면
		// 캘린더에 뒤죽박죽 순서로 들어가고, 같은 요청이 두 번 왔을 때
		// 결과가 다른지 비교하기도 어려워진다.
		BigRoutineCreationPlan plan = datesPlan(List.of(
			LocalDate.of(2026, 9, 17),
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 12)));

		assertThat(plan.routineDates()).containsExactly(
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 12),
			LocalDate.of(2026, 9, 17));
	}

	@Test
	@DisplayName("DATES 에 같은 날짜가 두 번 오면 하나로 친다")
	void duplicateDatesAreCollapsed() {
		// 그대로 두면 같은 날에 똑같은 루틴이 두 개 생긴다. 사용자가
		// 실수로 같은 날을 두 번 골랐을 뿐인데 지우는 일이 두 번이 된다.
		BigRoutineCreationPlan plan = datesPlan(List.of(
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 10),
			LocalDate.of(2026, 9, 12)));

		assertThat(plan.createdCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("DATES 가 비어 있으면 거절한다")
	void emptyDatesIsRejected() {
		assertThatThrownBy(() -> datesPlan(List.of()))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
	}

	@Test
	@DisplayName("DATES 는 열두 개까지 받고 열세 개는 거절한다")
	void datesLimitIsTwelve() {
		LocalDate start = LocalDate.of(2026, 9, 1);

		List<LocalDate> twelveDates = Stream.iterate(start, date -> date.plusDays(1))
			.limit(MAX_DATE_COUNT)
			.toList();

		assertThat(datesPlan(twelveDates).createdCount()).isEqualTo(MAX_DATE_COUNT);

		List<LocalDate> thirteenDates = Stream.iterate(start, date -> date.plusDays(1))
			.limit(MAX_DATE_COUNT + 1)
			.toList();

		assertThatThrownBy(() -> datesPlan(thirteenDates))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_TOO_MANY_DATES);
	}

	@Test
	@DisplayName("중복을 걷어낸 뒤의 개수로 상한을 본다")
	void dateLimitIsCheckedAfterRemovingDuplicates() {
		// 같은 날짜를 열세 번 보내면 실제로 만들어지는 것은 하나뿐이다.
		// 중복을 걷어내기 전에 세면 만들 것이 하나인 요청을 거절하게 된다.
		List<LocalDate> repeated = Stream.generate(() -> LocalDate.of(2026, 9, 10))
			.limit(MAX_DATE_COUNT + 1)
			.toList();

		assertThat(datesPlan(repeated).createdCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("DATES 로 만든 날짜도 모두 같은 seriesId 를 공유한다")
	void datesShareOneSeriesId() {
		BigRoutineCreationPlan plan = datesPlan(List.of(
			LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)));

		assertThat(plan.seriesId()).isNotNull();
		assertThat(plan.createdCount()).isEqualTo(2);
	}

	// ------------------------------------------------------------------
	// 도우미
	// ------------------------------------------------------------------

	private static BigRoutineCreationPlan plan(LocalDate startDate, LocalDate endDate) {
		return BigRoutineCreationPlan.ofRange(
			"아침 준비", SEVEN_THIRTY, EIGHT_THIRTY, startDate, endDate, MAX_DATE_RANGE_LENGTH);
	}

	private static BigRoutineCreationPlan weeklyPlan(
		LocalDate startDate, LocalDate endDate, Set<DayOfWeek> repeatDays) {

		return BigRoutineCreationPlan.ofWeekly(
			"아침 준비", SEVEN_THIRTY, EIGHT_THIRTY,
			startDate, endDate, repeatDays, MAX_DATE_RANGE_LENGTH);
	}

	private static BigRoutineCreationPlan datesPlan(List<LocalDate> dates) {
		return BigRoutineCreationPlan.ofDates(
			"아침 준비", SEVEN_THIRTY, EIGHT_THIRTY, dates, MAX_DATE_COUNT);
	}

}
