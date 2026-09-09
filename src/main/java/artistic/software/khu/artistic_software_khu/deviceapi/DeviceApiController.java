package artistic.software.khu.artistic_software_khu.deviceapi;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedDevice;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import artistic.software.khu.artistic_software_khu.device.ClaimRequest;
import artistic.software.khu.artistic_software_khu.device.ClaimResponse;
import artistic.software.khu.artistic_software_khu.device.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 디바이스 API (기기용). "API.md" 8장.
 *
 * "device" 패키지와 나누어 둔 이유는 인증 방식이 다르기 때문이다(CLAUDE.md 6장).
 * 앱은 "X-Access-Uuid" 로 USERS 를 찾고 기기는 "X-Device-Uuid" 로 DEVICES 를
 * 찾는다. 한 패키지에 섞으면 어느 체인이 어느 컨트롤러를 태우는지 흐려진다.
 *
 * claim 만은 **인증 없이** 호출된다. 그 시점의 기기는 아직 신분증이 없고,
 * 신분증을 받으려고 이 호출을 하는 것이기 때문이다("API.md" 1-1 화이트리스트).
 */
@RestController
@RequestMapping("/device-api/v1")
public class DeviceApiController {

	private final DeviceService deviceService;

	private final DeviceSyncService deviceSyncService;

	public DeviceApiController(
		DeviceService deviceService, DeviceSyncService deviceSyncService) {

		this.deviceService = deviceService;
		this.deviceSyncService = deviceSyncService;
	}

	@PostMapping("/claim")
	public ResponseEntity<ApiResponse<ClaimResponse>> claim(@RequestBody ClaimRequest request) {
		return ResponseEntity.ok(ApiResponse.success(deviceService.claim(request)));
	}

	/**
	 * 기기 동기화. claim 과 달리 인증이 필요하다.
	 *
	 * 인증된 기기 정보는 필터가 SecurityContext 에 넣어 둔 것을 받는다.
	 * 요청 본문에서 기기 id 를 받지 않는 이유는, 그러면 기기가 남의 id 를
	 * 지어내 다른 아이의 루틴을 받아 갈 수 있기 때문이다.
	 */
	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<SyncResponse>> sync(
		@AuthenticationPrincipal AuthenticatedDevice authenticatedDevice,
		@RequestBody SyncRequest request) {

		return ResponseEntity.ok(ApiResponse.success(
			deviceSyncService.sync(authenticatedDevice.deviceId(), request)));
	}

}
