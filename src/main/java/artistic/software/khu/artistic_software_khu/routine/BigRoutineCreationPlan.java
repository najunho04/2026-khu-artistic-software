package artistic.software.khu.artistic_software_khu.routine;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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
 * 반복 모드는 세 가지이고 서로 배타적이다("API.md" 9장). 모드마다 만드는 방법이
 * 따로 있지만, 만들어진 뒤의 모양은 셋 다 같다. 날짜 목록과 seriesId 하나뿐이다.
 * 그래서 "BIG_ROUTINES" 에 반복 관련 컬럼이 하나도 없다. 행이 만들어진 뒤에는
 * 그것이 어느 모드에서 나왔는지 알 필요가 없다.
 *
 * @param seriesId     이 계획으로 만들어질 행들이 공유할 미션 식별자
 * @param title        빅루틴 제목
 * @param startTime    시작 시각. KST 벽시계 시각으로 해석한다
 * @param endTime      종료 시각. startTime 보다 뒤여야 한다
 * @param routineDates 실제로 행을 만들 날짜 목록. 항상 날짜순이고 중복이 없다
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
	 * RANGE — 기간 안의 매일.
	 *
	 * @param endDate            없으면 startDate 하루만 만든다. "API.md" 의 규칙이다
	 * @param maxDateRangeLength 한 번에 만들 수 있는 최대 날짜 수. 상한값이 아직
	 *                           확정되지 않아 설정값으로 주입받는다. 상한이 없으면
	 *                           한 번의 요청으로 몇 년치 행이 만들어질 수 있다
	 */
	public static BigRoutineCreationPlan ofRange(
		String title,
		LocalTime startTime,
		LocalTime endTime,
		LocalDate startDate,
		LocalDate endDate,
		int maxDateRangeLength) {

		validateTimeRange(startTime, endTime);

		LocalDate lastDate = (endDate == null) ? startDate : endDate;
		long dateCount = validateAndCountDateRange(startDate, lastDate, maxDateRangeLength);

		List<LocalDate> routineDates = Stream
			.iterate(startDate, date -> date.plusDays(1))
			.limit(dateCount)
			.toList();

		return create(title, startTime, endTime, routineDates);
	}

	/**
	 * WEEKLY — 기간 안에서 지정한 요일마다.
	 *
	 * 상한을 "만들어지는 행 개수" 가 아니라 "훑는 기간" 에 거는 것이 중요하다.
	 * 매주 월요일이면 만들어지는 행은 적지만 훑어야 하는 날짜는 기간 전체다.
	 * 기간에 상한이 없으면 "매주 월요일, 10년치" 같은 요청이 3650일을 훑는다.
	 *
	 * @param repeatDays 비어 있으면 거절한다. 요일을 아예 고르지 않은 요청은
	 *                   성립하지 않는다
	 */
	public static BigRoutineCreationPlan ofWeekly(
		String title,
		LocalTime startTime,
		LocalTime endTime,
		LocalDate startDate,
		LocalDate endDate,
		Set<DayOfWeek> repeatDays,
		int maxDateRangeLength) {

		validateTimeRange(startTime, endTime);

		if (repeatDays == null || repeatDays.isEmpty()) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}

		long scannedDayCount = validateAndCountDateRange(startDate, endDate, maxDateRangeLength);

		List<LocalDate> routineDates = Stream
			.iterate(startDate, date -> date.plusDays(1))
			.limit(scannedDayCount)
			.filter(date -> repeatDays.contains(date.getDayOfWeek()))
			.toList();

		// 여기서 목록이 비어도 오류가 아니다. 사용자가 "다음 주 월요일부터" 를
		// 기대하고 짧은 기간을 골랐을 뿐이다. 오류로 막으면 왜 안 되는지 알 수
		// 없으므로, 0개를 돌려주고 앱이 "만들어진 루틴이 없습니다" 를 보여준다.
		// 요일을 아예 고르지 않은 위의 경우와는 다르다.
		return create(title, startTime, endTime, routineDates);
	}

	/**
	 * DATES — 지정한 날짜들만.
	 *
	 * 중복을 걷어내고 날짜순으로 정리한 "뒤에" 개수 상한을 본다. 순서가 반대면
	 * 같은 날짜를 열세 번 보낸 요청을 거절하게 되는데, 그 요청이 실제로 만드는
	 * 것은 하나뿐이다.
	 *
	 * @param maxDateCount 지정할 수 있는 날짜 개수. "API.md" 9장이 12개로 확정했다
	 */
	public static BigRoutineCreationPlan ofDates(
		String title,
		LocalTime startTime,
		LocalTime endTime,
		Collection<LocalDate> repeatDates,
		int maxDateCount) {

		validateTimeRange(startTime, endTime);

		if (repeatDates == null || repeatDates.isEmpty()) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}

		// 중복을 그대로 두면 같은 날에 똑같은 루틴이 두 개 생긴다. 사용자가
		// 실수로 같은 날을 두 번 골랐을 뿐인데 지우는 일이 두 번이 된다.
		//
		// 정렬하는 이유는 앱이 어떤 순서로 보내든 결과가 같아야 하기 때문이다.
		// 정리하지 않으면 캘린더에 뒤죽박죽으로 들어가고, 같은 요청이 두 번
		// 왔을 때 결과가 같은지 비교하기도 어려워진다.
		List<LocalDate> routineDates = repeatDates.stream()
			.distinct()
			.sorted()
			.toList();

		if (routineDates.size() > maxDateCount) {
			throw new BusinessException(ErrorCode.ROUTINE_TOO_MANY_DATES);
		}

		return create(title, startTime, endTime, routineDates);
	}

	/**
	 * 만들어질 행의 개수. "API.md" 응답의 createdCount 가 이 값이다.
	 */
	public int createdCount() {
		return routineDates.size();
	}

	/**
	 * UUID 를 "여기서 한 번만" 만든다. 세 모드가 전부 이 자리를 거치므로
	 * 모드를 더해도 시리즈가 쪼개질 자리가 생기지 않는다.
	 */
	private static BigRoutineCreationPlan create(
		String title, LocalTime startTime, LocalTime endTime, List<LocalDate> routineDates) {

		return new BigRoutineCreationPlan(
			UUID.randomUUID(), title, startTime, endTime, routineDates);
	}

	/**
	 * "API.md" 가 endTime 은 startTime 보다 "뒤" 여야 한다고 적었다.
	 * 같은 값은 뒤가 아니므로 길이가 0 인 루틴도 거절한다.
	 */
	private static void validateTimeRange(LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_TIME_RANGE);
		}
	}

	/**
	 * 기간을 검증하고 그 안의 날짜 수를 돌려준다. RANGE 와 WEEKLY 가 함께 쓴다.
	 */
	private static long validateAndCountDateRange(
		LocalDate startDate, LocalDate endDate, int maxDateRangeLength) {

		if (startDate == null || endDate == null) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}

		if (endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_DATE_RANGE);
		}

		// 시작일과 종료일을 모두 포함하므로 하루를 더한다.
		// 9월 1일부터 9월 7일까지는 6일이 아니라 7일이다.
		long dateCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;

		if (dateCount > maxDateRangeLength) {
			throw new BusinessException(ErrorCode.ROUTINE_DATE_RANGE_TOO_LONG);
		}

		return dateCount;
	}

}
