package artistic.software.khu.artistic_software_khu.character;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.common.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐릭터 API. "API.md" 12장.
 *
 * 조회 두 개뿐이다. 획득과 진화는 기기 동기화 안에서 일어나므로 앱이 부를
 * 엔드포인트가 없다.
 */
@RestController
@RequestMapping("/api/v1")
public class CharacterController {

	private final CharacterService characterService;

	public CharacterController(CharacterService characterService) {
		this.characterService = characterService;
	}

	@GetMapping("/characters")
	public ResponseEntity<ApiResponse<List<CharacterResponse>>> findAllCharacters() {
		return ResponseEntity.ok(ApiResponse.success(characterService.findAllCharacters()));
	}

	@GetMapping("/children/{childId}/characters")
	public ResponseEntity<ApiResponse<List<ChildCharacterResponse>>> findChildCharacters(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long childId) {

		return ResponseEntity.ok(ApiResponse.success(
			characterService.findChildCharacters(authenticatedUser.userId(), childId)));
	}

}
