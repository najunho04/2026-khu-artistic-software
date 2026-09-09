package artistic.software.khu.artistic_software_khu.auth;

import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. "API.md" 4장.
 *
 * signup 과 login 은 인증 없이 호출된다("API.md" 1-1 화이트리스트).
 * logout 은 누구를 로그아웃시킬지 알아야 하므로 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

	private final AuthenticationService authenticationService;

	public AuthenticationController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<AuthenticationResponse>> signUp(
		@RequestBody SignUpRequest request) {

		AuthenticationResponse response = authenticationService.signUp(request);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(response));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthenticationResponse>> logIn(
		@RequestBody LogInRequest request) {

		return ResponseEntity.ok(ApiResponse.success(authenticationService.logIn(request)));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logOut(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		authenticationService.logOut(authenticatedUser.userId());

		return ResponseEntity.noContent().build();
	}

}
