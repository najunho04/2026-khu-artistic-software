package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 빅루틴 하나와 그 안의 할 일들. "API.md" 9장.
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
		BigRoutine bigRoutine, List<SmallRoutine> smallRoutines) {

		return new BigRoutineResponse(
			bigRoutine.getId(),
			bigRoutine.getSeriesId(),
			bigRoutine.getTitle(),
			bigRoutine.getStartTime(),
			bigRoutine.getEndTime(),
			bigRoutine.getSortOrder(),
			smallRoutines.stream().map(SmallRoutineResponse::from).toList());
	}

}
