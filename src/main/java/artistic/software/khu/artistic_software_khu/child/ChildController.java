package artistic.software.khu.artistic_software_khu.child;

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
 * 자녀 API. "API.md" 6장.
 *
 * 모든 메서드가 인증된 유저의 id 를 받아 서비스로 넘긴다. 컨트롤러가 소유권을
 * 직접 판단하지 않는 이유는, 그 규칙이 루틴 도메인에서도 똑같이 쓰이기 때문이다.
 * 한 곳(ChildService.findOwnedChild)에 두어야 어느 경로에서든 같게 동작한다.
 */
@RestController
@RequestMapping("/api/v1/children")
public class ChildController {

	private final ChildService childService;

	public ChildController(ChildService childService) {
		this.childService = childService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ChildResponse>> register(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestBody ChildRequest request) {

		ChildResponse response = childService.register(authenticatedUser.userId(), request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<ChildResponse>>> findMyChildren(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

		return ResponseEntity.ok(
			ApiResponse.success(childService.findMyChildren(authenticatedUser.userId())));
	}

	@GetMapping("/{childId}")
	public ResponseEntity<ApiResponse<ChildResponse>> findOne(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		return ResponseEntity.ok(
			ApiResponse.success(childService.findOne(authenticatedUser.userId(), childId)));
	}

	@PatchMapping("/{childId}")
	public ResponseEntity<ApiResponse<ChildResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestBody ChildRequest request) {

		return ResponseEntity.ok(
			ApiResponse.success(childService.update(authenticatedUser.userId(), childId, request)));
	}

	@DeleteMapping("/{childId}")
	public ResponseEntity<Void> delete(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		childService.delete(authenticatedUser.userId(), childId);

		return ResponseEntity.noContent().build();
	}

}
