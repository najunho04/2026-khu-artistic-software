package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 빅루틴 하나와 그 안의 할 일들. "API.md" 9장.
 *
 * "sortOrder" 는 DB 컬럼에서 읽지 않고 조회할 때 계산해 넣는다. 하루 안에서
 * 시작 시각이 이른 것부터 1, 2, 3 이다. 저장해 두면 나중에 시각을 고쳤을 때
 * 순서가 시각과 어긋나고, 그것을 맞추려면 같은 날짜의 다른 행까지 함께
 * 고쳐야 한다("ERD.md" 1장 설계 노트).
 */
public record BigRoutineResponse(
	Long bigRoutineId,
	UUID seriesId,
	String title,
	LocalTime startTime,
	LocalTime endTime,
	Integer sortOrder,
	List<SmallRoutineResponse> smallRoutines) {

	public static BigRoutineResponse of(
		BigRoutine bigRoutine, List<SmallRoutine> smallRoutines, Integer sortOrder) {

		return new BigRoutineResponse(
			bigRoutine.getId(),
			bigRoutine.getSeriesId(),
			bigRoutine.getTitle(),
			bigRoutine.getStartTime(),
			bigRoutine.getEndTime(),
			sortOrder,
			smallRoutines.stream().map(SmallRoutineResponse::from).toList());
	}

}
