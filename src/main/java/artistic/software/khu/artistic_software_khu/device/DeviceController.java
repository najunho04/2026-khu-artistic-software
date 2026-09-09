package artistic.software.khu.artistic_software_khu.device;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기기 API (앱용). "API.md" 7장.
 *
 * 여기는 보호자가 부르는 쪽이다. 기기가 부르는 claim 과 sync 는
 * "/device-api/v1/**" 에 있고 인증 방식이 다르다.
 */
@RestController
@RequestMapping("/api/v1")
public class DeviceController {

	private final DeviceService deviceService;

	public DeviceController(DeviceService deviceService) {
		this.deviceService = deviceService;
	}

	@PostMapping("/devices/pairing")
	public ResponseEntity<ApiResponse<PairingResponse>> startPairing(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestBody PairingRequest request) {

		PairingResponse response = deviceService.startPairing(authenticatedUser.userId(), request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/children/{childId}/devices")
	public ResponseEntity<ApiResponse<List<DeviceResponse>>> findByChild(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		return ResponseEntity.ok(
			ApiResponse.success(deviceService.findByChild(authenticatedUser.userId(), childId)));
	}

	@GetMapping("/devices/{deviceId}")
	public ResponseEntity<ApiResponse<DeviceResponse>> findOne(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long deviceId) {

		return ResponseEntity.ok(
			ApiResponse.success(deviceService.findOne(authenticatedUser.userId(), deviceId)));
	}

	@PatchMapping("/devices/{deviceId}")
	public ResponseEntity<ApiResponse<DeviceResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long deviceId,
		@RequestBody DeviceUpdateRequest request) {

		return ResponseEntity.ok(ApiResponse.success(
			deviceService.update(authenticatedUser.userId(), deviceId, request)));
	}

	@DeleteMapping("/devices/{deviceId}")
	public ResponseEntity<Void> release(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long deviceId) {

		deviceService.release(authenticatedUser.userId(), deviceId);

		return ResponseEntity.noContent().build();
	}

}
