package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalTime;
import java.util.List;

/**
 * 양식 응답. "API.md" 10장.
 *
 * isActive 가 없다. 그 컬럼은 "자동 생성을 중단한다" 는 뜻이었는데
 * 자동 생성 자체가 없어져 의미가 남지 않아 삭제했다.
 */
public record RoutineTemplateResponse(
	Long templateId,
	String title,
	LocalTime startTime,
	LocalTime endTime,
	List<SmallRoutineRequest> smallRoutines) {
}
