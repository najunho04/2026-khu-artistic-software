package artistic.software.khu.artistic_software_khu.routine;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 빅루틴 생성 요청을 "실제로 만들 행들" 로 펼쳐 놓은 계획.
 *
 * 저장은 하지 않는다. 며칠치를 만들지, 어떤 미션으로 묶을지, 어떤 요청을 거절할지만
 * 정한다. 저장 방법과 규칙을 갈라 두면 규칙을 DB 없이 검증할 수 있다.
 *
 * 이 클래스가 따로 있는 가장 큰 이유는 seriesId 다. 날짜별 행을 만드는 반복문
 * 안에서 UUID 를 만들면 날짜마다 다른 값이 붙어 "원래 같은 미션" 이라는 표시가
 * 쪼개진다. 그러면 미션별 이행률 통계가 날짜별로 흩어지는데, 오류가 나지 않으므로
 * 숫자가 틀렸다는 것을 아무도 알아차리지 못한다.
 *
 * 계획이 seriesId 를 하나만 들고 있으면 그 실수를 할 자리 자체가 없어진다.
 * 만드는 쪽은 이 값을 모든 행에 그대로 넣기만 하면 된다.
 *
 * @param seriesId     이 계획으로 만들어질 행들이 공유할 미션 식별자
 * @param title        빅루틴 제목
 * @param startTime    시작 시각. KST 벽시계 시각으로 해석한다
 * @param endTime      종료 시각. startTime 보다 뒤여야 한다
 * @param routineDates 실제로 행을 만들 날짜 목록. 시작일과 종료일을 모두 포함한다
 */
public record BigRoutineCreationPlan(
	UUID seriesId,
	String title,
	LocalTime startTime,
	LocalTime endTime,
	List<LocalDate> routineDates) {

	public BigRoutineCreationPlan {
		routineDates = List.copyOf(routineDates);
	}

	/**
	 * 요청 값을 검증하고 계획을 만든다.
	 *
	 * @param endDate            없으면 startDate 하루만 만든다. "API.md" 의 규칙이다
	 * @param maxDateRangeLength 한 번에 만들 수 있는 최대 날짜 수. 상한값이 아직
	 *                           확정되지 않아 설정값으로 주입받는다. 상한이 없으면
	 *                           한 번의 요청으로 몇 년치 행이 만들어질 수 있다
	 */
	public static BigRoutineCreationPlan of(
		String title,
		LocalTime startTime,
		LocalTime endTime,
		LocalDate startDate,
		LocalDate endDate,
		int maxDateRangeLength) {

		// "API.md" 가 endTime 은 startTime 보다 "뒤" 여야 한다고 적었다.
		// 같은 값은 뒤가 아니므로 길이가 0 인 루틴도 거절한다.
		if (!endTime.isAfter(startTime)) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_TIME_RANGE);
		}

		LocalDate lastDate = (endDate == null) ? startDate : endDate;

		if (lastDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_DATE_RANGE);
		}

		// 시작일과 종료일을 모두 포함하므로 하루를 더한다.
		// 9월 1일부터 9월 7일까지는 6일이 아니라 7일이다.
		long dateCount = ChronoUnit.DAYS.between(startDate, lastDate) + 1;

		if (dateCount > maxDateRangeLength) {
			throw new BusinessException(ErrorCode.ROUTINE_DATE_RANGE_TOO_LONG);
		}

		// UUID 를 "여기서 한 번만" 만든다. 아래 목록의 모든 날짜가 이 값을 공유한다.
		UUID seriesId = UUID.randomUUID();

		List<LocalDate> routineDates = Stream
			.iterate(startDate, date -> date.plusDays(1))
			.limit(dateCount)
			.toList();

		return new BigRoutineCreationPlan(seriesId, title, startTime, endTime, routineDates);
	}

	/**
	 * 만들어질 행의 개수. "API.md" 응답의 createdCount 가 이 값이다.
	 */
	public int createdCount() {
		return routineDates.size();
	}

}
