package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 루틴. 기기 동기화 응답에 실린다("API.md" 8장).
 *
 * 캘린더 응답(CalendarDayResponse)과 달리 이행률 요약이 없다. 기기는 그 숫자를
 * 쓸 데가 없고, 화면도 없다. 기기가 하는 일은 정해진 시각에 할 일을 보여주고
 * 완료를 올리는 것뿐이다.
 *
 * 응답을 작게 유지하는 것이 중요한 이유는 받는 쪽이 임베디드 장치여서다.
 * 쓰지 않는 필드도 파싱하는 동안 메모리를 차지한다.
 */
public record RoutineDayResponse(LocalDate date, List<BigRoutineResponse> bigRoutines) {
}
