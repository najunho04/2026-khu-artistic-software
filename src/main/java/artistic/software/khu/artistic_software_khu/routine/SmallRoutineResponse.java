package artistic.software.khu.artistic_software_khu.routine;

import java.time.Instant;
import java.util.UUID;

/**
 * 할 일 하나. "API.md" 9장 응답의 smallRoutines 원소다.
 */
public record SmallRoutineResponse(
	Long smallRoutineId,
	UUID seriesId,
	String title,
	Integer sortOrder,
	String status,
	Instant completedAt) {

	public static SmallRoutineResponse from(SmallRoutine smallRoutine) {
		return new SmallRoutineResponse(
			smallRoutine.getId(),
			smallRoutine.getSeriesId(),
			smallRoutine.getTitle(),
			smallRoutine.getSortOrder(),
			smallRoutine.getStatus(),
			smallRoutine.getCompletedAt());
	}

}
