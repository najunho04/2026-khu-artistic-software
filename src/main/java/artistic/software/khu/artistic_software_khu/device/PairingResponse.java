package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;

/**
 * 페어링 코드 발급 응답. "API.md" 7장.
 *
 * deviceId 가 들어 있는 이유는 자녀당 기기가 여러 대라서다. PENDING 행이 동시에
 * 여럿일 수 있어 childId 만으로는 방금 만든 행을 특정할 수 없고, 온보딩 마지막
 * 폴링("GET /devices/{deviceId}")에 이 값이 필요하다.
 *
 * **pairingCode 를 응답에 담는 것은 여기 한 번뿐이다.** 이후 조회에서는 주지
 * 않는다. 조회 때마다 다시 실어 보내면 코드가 살아 있는 10분 동안 새어 나갈
 * 자리가 늘어난다.
 */
public record PairingResponse(
	Long deviceId,
	String pairingCode,
	Instant expiresAt,
	String status) {

	public static PairingResponse from(Device device) {
		return new PairingResponse(
			device.getId(),
			device.getPairingCode(),
			device.getPairingCodeExpiresAt(),
			device.getStatus());
	}

}
