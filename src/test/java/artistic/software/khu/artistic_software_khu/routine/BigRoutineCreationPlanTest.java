package artistic.software.khu.artistic_software_khu.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
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
		assertThatThrownBy(() -> BigRoutineCreationPlan.of(
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
		assertThatThrownBy(() -> BigRoutineCreationPlan.of(
			"아침 준비", SEVEN_THIRTY, SEVEN_THIRTY,
			LocalDate.of(2026, 9, 1), null, MAX_DATE_RANGE_LENGTH))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.ROUTINE_INVALID_TIME_RANGE);
	}

	private static BigRoutineCreationPlan plan(LocalDate startDate, LocalDate endDate) {
		return BigRoutineCreationPlan.of(
			"아침 준비", SEVEN_THIRTY, EIGHT_THIRTY, startDate, endDate, MAX_DATE_RANGE_LENGTH);
	}

}
