package artistic.software.khu.artistic_software_khu.deviceapi;

import artistic.software.khu.artistic_software_khu.routine.RoutineDayResponse;
import java.time.Instant;
import java.util.List;

/**
 * 기기 동기화 응답. "API.md" 8장.
 *
 * @param serverTime 기기가 자기 시계를 맞추는 데 쓴다. 기기에는 정확한 시계가
 *                   없어 시간이 지나면 어긋나는데, 루틴이 "아침 7시 30분" 처럼
 *                   시각에 매여 있어 어긋나면 엉뚱한 때에 알린다
 * @param accepted   실제로 반영된 완료 기록 수. 보낸 수와 다를 수 있다.
 *                   기기가 오프라인인 동안 보호자가 지운 할 일이 섞이면
 *                   그것만 건너뛰기 때문이다
 * @param routines   요청한 날짜들의 루틴. 없는 날짜는 그냥 빠진다
 */
public record SyncResponse(Instant serverTime, int accepted, List<RoutineDayResponse> routines) {
}
