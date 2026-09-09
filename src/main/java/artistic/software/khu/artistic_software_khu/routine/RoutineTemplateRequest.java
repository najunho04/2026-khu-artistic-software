package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalTime;
import java.util.List;

/**
 * 양식 저장 · 수정 요청. "API.md" 10장.
 *
 * 반복 관련 필드가 없다. 반복은 빅루틴을 만들 때 정한다(9장 repeatType).
 */
public record RoutineTemplateRequest(
	String title,
	LocalTime startTime,
	LocalTime endTime,
	List<SmallRoutineRequest> smallRoutines) {
}
