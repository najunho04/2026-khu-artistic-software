package artistic.software.khu.artistic_software_khu.dashboard;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.LocalDate;

/**
 * 통계 기간. "API.md" 11-1 이 정한 세 구간을 날짜 범위로 바꾼다.
 *
 * 이 계산을 한 곳에 몰아넣는 이유는 세 엔드포인트가 **같은 규칙을 써야** 하기
 * 때문이다. 각자 날짜를 계산하면 대시보드의 주간 수치와 "stats?period=WEEK" 가
 * 다르게 나올 수 있는데, 그 차이는 보통 하루짜리라 눈으로는 알아차리기 어렵다.
 *
 * **달력 기준이 아니라 "최근 N일" 이다.** 달력 주(월요일 시작)로 하면 월요일
 * 아침에 이행률이 0% 로 보여 사용자가 실패한 것처럼 느낀다. "주의 시작이
 * 월요일인가 일요일인가" 를 정해야 하는 문제도 사라진다.
 *
 * @param from 시작일 (포함)
 * @param to   종료일 (포함). 항상 기준일이다
 */
public record StatsPeriod(String name, LocalDate from, LocalDate to) {

	private static final String DAY = "DAY";

	private static final String WEEK = "WEEK";

	private static final String MONTH = "MONTH";

	private static final int WEEK_DAY_COUNT = 7;

	private static final int MONTH_DAY_COUNT = 30;

	/**
	 * 기간 이름과 기준일로 날짜 범위를 만든다.
	 *
	 * @param today 기준일. KST 기준 오늘이며, 부르는 쪽이 넘긴다.
	 *              여기서 직접 구하지 않는 이유는 그러면 테스트에서 날짜를
	 *              고정할 수 없어 "오늘이 언제냐" 에 따라 결과가 달라지기 때문이다
	 */
	public static StatsPeriod of(String name, LocalDate today) {
		if (name == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		String normalized = name.toUpperCase();

		return switch (normalized) {
			case DAY -> new StatsPeriod(DAY, today, today);
			case WEEK -> new StatsPeriod(WEEK, minusDays(today, WEEK_DAY_COUNT), today);
			case MONTH -> new StatsPeriod(MONTH, minusDays(today, MONTH_DAY_COUNT), today);

			// 앱이 오타를 냈을 때 조용히 기본값으로 넘어가면, 화면에 엉뚱한
			// 기간의 숫자가 뜨는데 아무도 이상하다고 생각하지 않는다.
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
	}

	/**
	 * 며칠짜리 기간인지. 시작일과 종료일을 모두 포함한 수다.
	 */
	public int dayCount() {
		return (int) (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1);
	}

	/**
	 * 오늘을 "포함한" N일이므로 N 이 아니라 N-1 을 뺀다.
	 *
	 * 7일이면 8월 26일 ~ 9월 1일이지 8월 25일 ~ 9월 1일이 아니다.
	 * 여기서 하루가 어긋나기 쉬워 따로 떼어 두었다.
	 */
	private static LocalDate minusDays(LocalDate today, int dayCount) {
		return today.minusDays(dayCount - 1L);
	}

}
