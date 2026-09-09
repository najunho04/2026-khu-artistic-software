package artistic.software.khu.artistic_software_khu.routine;

import java.util.List;

/**
 * 스몰루틴 순서 변경 요청. "API.md" 9장.
 *
 * 바뀐 순서대로 id 를 전부 담아 보낸다. 하나라도 빠지면 거절한다.
 * 일부만 보내면 나머지의 순서를 서버가 짐작해야 하는데, 그 짐작이
 * 사용자가 화면에서 본 것과 다를 수 있다.
 */
public record SmallRoutineOrderRequest(List<Long> smallRoutineIds) {
}
