package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 빅루틴 생성 응답. "API.md" 9장.
 */
public record BigRoutineCreateResponse(
	UUID seriesId,
	List<LocalDate> createdDates,
	int createdCount) {

	public static BigRoutineCreateResponse from(BigRoutineCreationPlan plan) {
		return new BigRoutineCreateResponse(
			plan.seriesId(), plan.routineDates(), plan.createdCount());
	}

}
