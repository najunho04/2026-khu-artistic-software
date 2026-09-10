package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;

/**
 * 기기 한 대의 상태. "API.md" 7장 GET /devices/:deviceId · PATCH /devices/:deviceId.
 *
 * 목록 응답(DeviceResponse)과 따로 두는 이유는 문서가 담는 값이 다르기 때문이다.
 * 단건 응답에만 childId 가 있다. 목록은 이미 "GET /children/:childId/devices" 처럼
 * 자녀 아래에서 부르므로 어느 자녀인지가 경로에 드러나 있지만, 단건은
 * "/devices/:deviceId" 라 경로만 봐서는 알 수 없다. 앱이 기기 화면에서
 * 자녀 이름을 보여주려면 이 값이 필요하다.
 */
public record DeviceDetailResponse(
	Long deviceId,
	Long childId,
	String nickname,
	String status,
	Integer battery,
	String firmwareVersion,
	Instant lastSyncedAt,
	Instant pairedAt) {

	public static DeviceDetailResponse from(Device device) {
		return new DeviceDetailResponse(
			device.getId(),
			device.getChildId(),
			device.getNickname(),
			device.getStatus(),
			device.getBatteryLevel(),
			device.getFirmwareVersion(),
			device.getLastSyncedAt(),
			device.getPairedAt());
	}

}
