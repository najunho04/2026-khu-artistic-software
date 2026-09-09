package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalTime;

/**
 * 빅루틴 수정 요청. "API.md" 9장. 셋 다 선택이고 보낸 것만 바뀐다.
 *
 * 스몰루틴 목록이 없는 것은 의도한 것이다. 할 일의 추가 · 삭제는 별도
 * 엔드포인트가 맡는다. 개수가 바뀌면 이행률이 움직이므로 전파 범위를
 * 다르게 다뤄야 하기 때문이다.
 */
public record BigRoutineUpdateRequest(String title, LocalTime startTime, LocalTime endTime) {
}
