package artistic.software.khu.artistic_software_khu.deviceapi;

import java.time.Instant;

/**
 * 기기가 올리는 완료 기록 하나. "API.md" 8장.
 *
 * completedAt 을 기기가 보내는 이유는 오프라인 동안 쌓인 기록을 나중에 한꺼번에
 * 올리기 때문이다. 서버가 받은 시각으로 적으면 아침 7시에 한 일이 저녁 8시에
 * 한 것으로 기록된다.
 */
public record CompletionRecord(Long smallRoutineId, String status, Instant completedAt) {
}
