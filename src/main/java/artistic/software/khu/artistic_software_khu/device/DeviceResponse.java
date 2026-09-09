package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;

/**
 * 기기 상태 응답. "API.md" 7장.
 *
 * **pairingCode 가 없다.** 발급 응답에서 한 번 준 것으로 끝이며, 조회에서는
 * 주지 않는다. 앱이 이 값을 다시 받을 이유가 없고, 실어 보낼수록 새어 나갈
 * 자리만 늘어난다.
 *
 * **battery · firmwareVersion · lastSyncedAt 은 null 일 수 있다.** 페어링만 하고
 * 아직 한 번도 sync 하지 않은 기기(PENDING)가 그렇다. 0 이나 현재 시각으로
 * 채우지 않는 이유는 "배터리 0%" 와 "아직 모름" 이 완전히 다른 뜻이고, 앱이
 * 그 둘을 구분해 보여줘야 하기 때문이다. 앱은 이 null 을 반드시 처리해야 한다.
 *
 * 앱은 온보딩 마지막 단계에서 이 응답의 status 가 PENDING 에서 ACTIVE 로
 * 바뀌는지를 폴링한다. 핫스팟 전달이 단방향이라 앱이 성공 여부를 알 방법이
 * 이것뿐이다.
 */
public record DeviceResponse(
	Long deviceId,
	String nickname,
	String status,
	Integer battery,
	String firmwareVersion,
	Instant lastSyncedAt,
	Instant pairedAt) {

	public static DeviceResponse from(Device device) {
		return new DeviceResponse(
			device.getId(),
			device.getNickname(),
			device.getStatus(),
			device.getBatteryLevel(),
			device.getFirmwareVersion(),
			device.getLastSyncedAt(),
			device.getPairedAt());
	}

}
