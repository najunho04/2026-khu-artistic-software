package artistic.software.khu.artistic_software_khu.dashboard;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계와 대시보드 API. "API.md" 11장.
 *
 * period 를 문자열로 받아 서비스가 검증한다. 열거형으로 받으면 잘못된 값이
 * 스프링 단계에서 걸려 공통 error 형태가 아닌 응답이 나간다.
 */
@RestController
@RequestMapping("/api/v1/children/{childId}")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<StatsSummary>> findStats(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestParam(required = false) String period) {

		return ResponseEntity.ok(ApiResponse.success(
			statsService.findStats(authenticatedUser.userId(), childId, period)));
	}

	@GetMapping("/stats/missions")
	public ResponseEntity<ApiResponse<List<MissionStatsResponse>>> findMissionStats(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId,
		@RequestParam(required = false) String period) {

		return ResponseEntity.ok(ApiResponse.success(
			statsService.findMissionStats(authenticatedUser.userId(), childId, period)));
	}

	@GetMapping("/dashboard")
	public ResponseEntity<ApiResponse<DashboardResponse>> findDashboard(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		return ResponseEntity.ok(ApiResponse.success(
			statsService.findDashboard(authenticatedUser.userId(), childId)));
	}

}
