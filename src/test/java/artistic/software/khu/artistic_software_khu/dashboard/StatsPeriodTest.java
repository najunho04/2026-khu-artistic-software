package artistic.software.khu.artistic_software_khu.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 통계 기간 계산. "API.md" 11-1.
 *
 * 이 조각을 따로 두는 이유는 세 엔드포인트가 **같은 규칙을 써야** 하기 때문이다.
 * 각자 날짜를 계산하면 대시보드의 주간 수치와 "stats?period=WEEK" 가 다르게
 * 나올 수 있고, 그 차이는 하루짜리라 눈으로 보고는 알아차리기 어렵다.
 *
 * 날짜 계산일 뿐이라 DB 없이 전부 검증할 수 있다.
 */
class StatsPeriodTest {

	// 기준일을 고정한다. 오늘을 쓰면 테스트가 언제 도느냐에 따라 결과가 달라진다.
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

	@Test
	@DisplayName("DAY 는 오늘 하루다")
	void dayIsToday() {
		StatsPeriod period = StatsPeriod.of("DAY", TODAY);

		assertThat(period.from()).isEqualTo(TODAY);
		assertThat(period.to()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("WEEK 는 오늘을 포함한 최근 7일이다")
	void weekIsLastSevenDaysIncludingToday() {
		StatsPeriod period = StatsPeriod.of("WEEK", TODAY);

		// 8월 26일부터 9월 1일까지가 7일이다. 6일을 빼는 것이지 7일이 아니다.
		// 오늘을 포함하기 때문이다. 여기서 하루가 어긋나기 쉽다.
		assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 26));
		assertThat(period.to()).isEqualTo(TODAY);
		assertThat(period.dayCount()).isEqualTo(7);
	}

	@Test
	@DisplayName("MONTH 는 오늘을 포함한 최근 30일이다")
	void monthIsLastThirtyDaysIncludingToday() {
		StatsPeriod period = StatsPeriod.of("MONTH", TODAY);

		assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 3));
		assertThat(period.to()).isEqualTo(TODAY);
		assertThat(period.dayCount()).isEqualTo(30);
	}

	@Test
	@DisplayName("달력 기준이 아니라 최근 N일이다")
	void periodIsRollingNotCalendarBased() {
		// 2026-08-31 은 월요일이다. 달력 주로 계산하면 이 날의 주는
		// 8월 31일 하루뿐이라 이행률이 거의 0 으로 보인다. 사용자는
		// 월요일 아침마다 "실패했다" 고 느끼게 된다.
		LocalDate monday = LocalDate.of(2026, 8, 31);

		StatsPeriod period = StatsPeriod.of("WEEK", monday);

		assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 25));
		assertThat(period.dayCount()).isEqualTo(7);
	}

	@Test
	@DisplayName("월을 넘어가도 날짜 수가 맞는다")
	void periodSpansMonthBoundaryCorrectly() {
		// 3월 2일에서 30일을 거슬러 올라가면 2월을 지난다. 2026년은 윤년이
		// 아니라 2월이 28일이다. 직접 빼면 틀리기 쉬운 자리다.
		StatsPeriod period = StatsPeriod.of("MONTH", LocalDate.of(2026, 3, 2));

		assertThat(period.from()).isEqualTo(LocalDate.of(2026, 2, 1));
		assertThat(period.dayCount()).isEqualTo(30);
	}

	@ParameterizedTest
	@DisplayName("소문자로 보내도 알아본다")
	@ValueSource(strings = {"day", "Day", "DAY"})
	void periodNameIsCaseInsensitive(String value) {
		assertThat(StatsPeriod.of(value, TODAY).from()).isEqualTo(TODAY);
	}

	@ParameterizedTest
	@DisplayName("정해진 셋이 아니면 거절한다")
	@ValueSource(strings = {"YEAR", "WEEKLY", "3DAYS", ""})
	void unknownPeriodIsRejected(String value) {
		// 앱이 오타를 냈을 때 조용히 기본값으로 넘어가면, 화면에 엉뚱한 기간의
		// 숫자가 뜨는데 아무도 이상하다고 생각하지 않는다.
		assertThatThrownBy(() -> StatsPeriod.of(value, TODAY))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("period 가 없으면 거절한다")
	void nullPeriodIsRejected() {
		assertThatThrownBy(() -> StatsPeriod.of(null, TODAY))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException) exception).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);
	}

}
