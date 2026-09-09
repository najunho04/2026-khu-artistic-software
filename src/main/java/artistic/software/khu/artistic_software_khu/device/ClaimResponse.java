package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;
import java.util.UUID;

/**
 * 기기 등록 응답. "API.md" 8장.
 *
 * **기기가 deviceAccessUuid 를 반드시 영속 저장해야 한다.** 잃어버리면 되찾을
 * 경로가 없어 재페어링뿐이다. 기기 토큰 체계를 없애면서 자체 복구 경로가
 * 사라진 것이 이 결정의 대가다.
 *
 * serverTime 은 기기가 자기 시계를 맞추는 데 쓴다. claim 직후에 한 번 맞춰두면
 * 첫 동기화 전까지의 시각 오차를 줄일 수 있다.
 */
public record ClaimResponse(Long deviceId, UUID deviceAccessUuid, Instant serverTime) {
}
