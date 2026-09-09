package artistic.software.khu.artistic_software_khu.dashboard;

import java.time.LocalDate;

/**
 * 한 기간의 성취도. "API.md" 11-1.
 *
 * n / N -> % 가 전부다. from 과 to 를 함께 담는 이유는 앱이 "8/26 ~ 9/1" 처럼
 * 기간을 화면에 쓸 수 있게 하기 위해서다. 앱이 다시 계산하면 서버와 하루가
 * 어긋날 수 있다.
 */
public record StatsSummary(
	String period,
	LocalDate from,
	LocalDate to,
	int doneCount,
	int totalCount,
	double completionRate) {

	public static StatsSummary of(StatsPeriod period, int doneCount, int totalCount) {
		return new StatsSummary(
			period.name(), period.from(), period.to(),
			doneCount, totalCount, rateOf(doneCount, totalCount));
	}

	/**
	 * 소수 첫째 자리까지 반올림한 백분율.
	 *
	 * 할 일이 하나도 없으면 0 이다. 0 으로 나눌 수 없고, 100 으로 두면
	 * 아무것도 안 한 기간이 만점으로 보여 통계가 부풀려진다.
	 */
	static double rateOf(int doneCount, int totalCount) {
		if (totalCount == 0) {
			return 0.0;
		}

		return Math.round(doneCount * 1000.0 / totalCount) / 10.0;
	}

}
