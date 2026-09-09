package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalDate;
import java.util.List;

/**
 * 캘린더의 하루. "API.md" 9장 GET /children/:childId/calendar.
 *
 * 요약(개수·이행률)과 상세(빅루틴 목록)를 함께 담는다. 앱은 이 응답을 들고
 * 있다가 사용자가 날짜를 누르면 서버를 다시 부르지 않고 여기서 꺼내 쓴다.
 * 그래서 날짜별 조회 엔드포인트가 따로 없다.
 *
 * @param totalCount     그 날의 할 일 총 개수. 이행률의 분모다
 * @param doneCount      완료한 할 일 개수. 이행률의 분자다
 * @param completionRate 백분율. 할 일이 하나도 없으면 0 이다
 */
public record CalendarDayResponse(
	LocalDate date,
	int totalCount,
	int doneCount,
	double completionRate,
	List<BigRoutineResponse> bigRoutines) {

	public static CalendarDayResponse of(
		LocalDate date, List<BigRoutineResponse> bigRoutines) {

		int totalCount = bigRoutines.stream()
			.mapToInt(bigRoutine -> bigRoutine.smallRoutines().size())
			.sum();

		int doneCount = (int) bigRoutines.stream()
			.flatMap(bigRoutine -> bigRoutine.smallRoutines().stream())
			.filter(smallRoutine -> SmallRoutine.STATUS_DONE.equals(smallRoutine.status()))
			.count();

		// 할 일이 없는 날을 0 으로 두는 이유는 0 으로 나눌 수 없기 때문이다.
		// 100 으로 두면 아무것도 안 한 날이 만점으로 보여 통계가 부풀려진다.
		double completionRate = (totalCount == 0)
			? 0.0
			: Math.round(doneCount * 1000.0 / totalCount) / 10.0;

		return new CalendarDayResponse(date, totalCount, doneCount, completionRate, bigRoutines);
	}

}
