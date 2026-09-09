package artistic.software.khu.artistic_software_khu.user;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 유저 API. "API.md" 5장.
 *
 * 경로에 유저 id 가 없다. 인증된 주체에서 꺼내 쓰기 때문이다. id 를 경로로
 * 받으면 남의 id 를 넣어 다른 사람의 정보를 볼 수 있는지 매번 검사해야 하는데,
 * "me" 로 두면 그럴 자리 자체가 없다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> findMe(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

		return ResponseEntity.ok(ApiResponse.success(
			userService.findMe(authenticatedUser.userId())));
	}

	@PatchMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> updateMe(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestBody UserUpdateRequest request) {

		return ResponseEntity.ok(ApiResponse.success(
			userService.updateMe(authenticatedUser.userId(), request)));
	}

}
