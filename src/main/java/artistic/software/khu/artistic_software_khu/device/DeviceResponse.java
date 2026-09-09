package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;

/**
 * 기기 상태 응답. "API.md" 7장.
 *
 * **pairingCode 가 없다.** 발급 응답에서 한 번 준 것으로 끝이며, 조회에서는
 * 주지 않는다. 앱이 이 값을 다시 받을 이유가 없고, 실어 보낼수록 새어 나갈
 * 자리만 늘어난다.
 *
 * 앱은 온보딩 마지막 단계에서 이 응답의 status 가 PENDING 에서 ACTIVE 로
 * 바뀌는지를 폴링한다. 핫스팟 전달이 단방향이라 앱이 성공 여부를 알 방법이
 * 이것뿐이다.
 */
public record DeviceResponse(
	Long deviceId,
	String nickname,
	String status,
	Instant pairedAt) {

	public static DeviceResponse from(Device device) {
		return new DeviceResponse(
			device.getId(), device.getNickname(), device.getStatus(), device.getPairedAt());
	}

}
