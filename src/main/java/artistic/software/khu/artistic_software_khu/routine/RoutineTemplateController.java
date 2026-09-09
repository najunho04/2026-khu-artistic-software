package artistic.software.khu.artistic_software_khu.routine;

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
 * 루틴 템플릿 API. "API.md" 10장.
 */
@RestController
@RequestMapping("/api/v1")
public class RoutineTemplateController {

	private final RoutineTemplateService routineTemplateService;

	public RoutineTemplateController(RoutineTemplateService routineTemplateService) {
		this.routineTemplateService = routineTemplateService;
	}

	@PostMapping("/children/{childId}/routine-templates")
	public ResponseEntity<ApiResponse<RoutineTemplateResponse>> save(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestBody RoutineTemplateRequest request) {

		RoutineTemplateResponse response =
			routineTemplateService.save(authenticatedUser.userId(), childId, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/children/{childId}/routine-templates")
	public ResponseEntity<ApiResponse<List<RoutineTemplateResponse>>> findAll(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		return ResponseEntity.ok(ApiResponse.success(
			routineTemplateService.findAll(authenticatedUser.userId(), childId)));
	}

	@PatchMapping("/routine-templates/{templateId}")
	public ResponseEntity<ApiResponse<RoutineTemplateResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long templateId,
		@RequestBody RoutineTemplateRequest request) {

		return ResponseEntity.ok(ApiResponse.success(
			routineTemplateService.update(authenticatedUser.userId(), templateId, request)));
	}

	@DeleteMapping("/routine-templates/{templateId}")
	public ResponseEntity<Void> delete(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long templateId) {

		routineTemplateService.delete(authenticatedUser.userId(), templateId);

		return ResponseEntity.noContent().build();
	}

}
