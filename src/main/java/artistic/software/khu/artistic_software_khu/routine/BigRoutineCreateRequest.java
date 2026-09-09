package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 빅루틴 생성 요청. "API.md" 9장.
 *
 * 모드마다 필요한 필드가 달라 대부분이 선택이다. 어느 조합이 유효한지는
 * 서비스가 판단하고, 어긋나면 ROUTINE_INVALID_REPEAT_RULE 로 거절한다.
 *
 * @param repeatDays  WEEKLY 전용. "MON" ~ "SUN"
 * @param repeatDates DATES 전용. 최대 12개
 * @param templateId  저장해둔 양식에서 꺼내 만들 때. 값이 복사될 뿐 연결은 남지 않는다
 */
public record BigRoutineCreateRequest(
	String title,
	LocalTime startTime,
	LocalTime endTime,
	String repeatType,
	LocalDate startDate,
	LocalDate endDate,
	List<String> repeatDays,
	List<LocalDate> repeatDates,
	List<SmallRoutineRequest> smallRoutines,
	Long templateId) {
}
