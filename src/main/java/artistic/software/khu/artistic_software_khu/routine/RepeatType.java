package artistic.software.khu.artistic_software_khu.routine;

/**
 * 빅루틴 반복 모드. "API.md" 9장에서 확정한 세 가지이고 서로 배타적이다.
 *
 * 이 값은 저장되지 않는다. 날짜를 펼치는 데에만 쓰이고, 행이 만들어진 뒤에는
 * "이 행들이 한 덩어리다" 라는 사실만 seriesId 로 남는다.
 */
public enum RepeatType {

	// 기간 안의 매일. 하루짜리 루틴은 시작일과 종료일을 같은 날로 준다.
	RANGE,

	// 기간 안에서 지정한 요일마다.
	WEEKLY,

	// 지정한 날짜들만. 최대 12개.
	DATES

}
