package artistic.software.khu.artistic_software_khu.routine;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 루틴 API. "API.md" 9장.
 *
 * 소유권 검사는 전부 서비스가 한다. 컨트롤러는 인증된 유저의 id 를 넘기기만
 * 한다. 규칙을 한 곳에 두어야 어느 경로에서든 같게 동작한다.
 */
@RestController
@RequestMapping("/api/v1")
public class RoutineController {

	private final RoutineService routineService;

	public RoutineController(RoutineService routineService) {
		this.routineService = routineService;
	}

	@PostMapping("/children/{childId}/big-routines")
	public ResponseEntity<ApiResponse<BigRoutineCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestBody BigRoutineCreateRequest request) {

		BigRoutineCreateResponse response =
			routineService.create(authenticatedUser.userId(), childId, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/children/{childId}/calendar")
	public ResponseEntity<ApiResponse<List<CalendarDayResponse>>> findCalendar(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

		return ResponseEntity.ok(ApiResponse.success(
			routineService.findCalendar(authenticatedUser.userId(), childId, from, to)));
	}

	@PatchMapping("/big-routines/{bigRoutineId}")
	public ResponseEntity<ApiResponse<BigRoutineResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long bigRoutineId,
		@RequestBody BigRoutineUpdateRequest request,
		@RequestParam(required = false) String scope) {

		return ResponseEntity.ok(ApiResponse.success(
			routineService.update(authenticatedUser.userId(), bigRoutineId, request, scope)));
	}

	@DeleteMapping("/big-routines/{bigRoutineId}")
	public ResponseEntity<Void> delete(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long bigRoutineId,
		@RequestParam(required = false) String scope) {

		routineService.delete(authenticatedUser.userId(), bigRoutineId, scope);

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/big-routines/{bigRoutineId}/small-routines")
	public ResponseEntity<ApiResponse<SmallRoutineResponse>> addSmallRoutine(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long bigRoutineId,
		@RequestBody SmallRoutineRequest request,
		@RequestParam(required = false) String scope) {

		SmallRoutineResponse response = routineService.addSmallRoutine(
			authenticatedUser.userId(), bigRoutineId, request, scope);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@PutMapping("/big-routines/{bigRoutineId}/small-routines/order")
	public ResponseEntity<ApiResponse<List<SmallRoutineResponse>>> reorder(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long bigRoutineId,
		@RequestBody SmallRoutineOrderRequest request) {

		return ResponseEntity.ok(ApiResponse.success(routineService.reorderSmallRoutines(
			authenticatedUser.userId(), bigRoutineId, request.smallRoutineIds())));
	}

	@PatchMapping("/small-routines/{smallRoutineId}")
	public ResponseEntity<ApiResponse<SmallRoutineResponse>> updateSmallRoutine(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long smallRoutineId,
		@RequestBody SmallRoutineRequest request) {

		return ResponseEntity.ok(ApiResponse.success(routineService.updateSmallRoutine(
			authenticatedUser.userId(), smallRoutineId, request)));
	}

	@DeleteMapping("/small-routines/{smallRoutineId}")
	public ResponseEntity<Void> deleteSmallRoutine(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long smallRoutineId) {

		routineService.deleteSmallRoutine(authenticatedUser.userId(), smallRoutineId);

		return ResponseEntity.noContent().build();
	}

}
