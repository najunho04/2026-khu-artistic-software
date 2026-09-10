package artistic.software.khu.artistic_software_khu.character;

import artistic.software.khu.artistic_software_khu.child.ChildService;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 캐릭터 도감 조회 · 보유 캐릭터 조회 · 성장. "API.md" 12장.
 *
 * 성장은 기기 동기화가 부른다. 앱에서 완료 표시를 해도 캐릭터는 자라지 않는다.
 * 아이가 실제로 기기 앞에서 한 일만 캐릭터에 반영한다는 뜻이다.
 */
@Service
public class CharacterService {

	private final CharacterRepository characterRepository;

	private final ChildCharacterRepository childCharacterRepository;

	private final ChildService childService;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	public CharacterService(
		CharacterRepository characterRepository,
		ChildCharacterRepository childCharacterRepository,
		ChildService childService,
		ObjectMapper objectMapper,
		Clock clock) {

		this.characterRepository = characterRepository;
		this.childCharacterRepository = childCharacterRepository;
		this.childService = childService;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * 도감 전체를 읽는다. 감춘 캐릭터는 빠진다.
	 *
	 * 도감이 비어 있으면 빈 목록이다. 캐릭터 디자인이 나오기 전까지 실제
	 * 서버가 이 상태이며, 오류가 아니다.
	 */
	@Transactional(readOnly = true)
	public List<CharacterResponse> findAllCharacters() {
		return characterRepository.findAllByIsActiveTrueOrderByCodeAsc().stream()
			.map(character -> CharacterResponse.of(character, readAssets(character)))
			.toList();
	}

	/**
	 * 자녀가 가진 캐릭터를 먼저 받은 순서로 읽는다.
	 *
	 * 소유권 검사는 자녀 도메인의 것을 그대로 쓴다. 없으면 404, 남의 것이면
	 * 403 이라는 판정이 한 곳에 모여 있어야 엔드포인트마다 결과가 달라지지 않는다.
	 */
	@Transactional(readOnly = true)
	public List<ChildCharacterResponse> findChildCharacters(Long userId, Long childId) {
		childService.findOwnedChild(userId, childId);

		List<ChildCharacter> childCharacters =
			childCharacterRepository.findAllByChildIdOrderByAcquiredAtAscIdAsc(childId);

		Map<Long, Character> charactersById = loadCharactersById(childCharacters);

		return childCharacters.stream()
			.map(childCharacter -> ChildCharacterResponse.of(
				childCharacter, charactersById.get(childCharacter.getCharacterId())))
			.toList();
	}

	/**
	 * 새로 완료된 미션 수만큼 경험치를 넣는다. 기기 동기화가 부른다.
	 *
	 * 경험치는 항상 "지금 키우는 캐릭터" 한 마리에만 들어간다. 그 마리가 다
	 * 자라면(경험치 30) 도감 순서로 다음 마리를 받아 남은 경험치를 이어서 넣는다.
	 * 그래서 반복문이다. 완료를 30개 넘게 몰아서 올리면 한 번의 동기화로 두
	 * 마리를 받을 수 있다.
	 *
	 * 도감이 비었거나 다 모았으면 아무 일도 하지 않는다. 오류가 아니며 남은
	 * 경험치는 버린다. 여기서 예외를 던지면 도감이 채워지기 전까지 기기가
	 * 동기화 자체를 못 하게 된다.
	 */
	@Transactional
	public void grantExperience(Long childId, int newlyCompletedCount) {
		int remainingExperience = newlyCompletedCount;

		while (remainingExperience > 0) {
			ChildCharacter growingCharacter = findGrowingCharacter(childId)
				.orElseGet(() -> grantNextCharacter(childId));

			if (growingCharacter == null) {
				return;
			}

			remainingExperience -= growingCharacter.addExperience(remainingExperience);
		}
	}

	/** 아직 다 자라지 않은 캐릭터. 자녀당 최대 한 마리다. */
	private Optional<ChildCharacter> findGrowingCharacter(Long childId) {
		return childCharacterRepository
			.findFirstByChildIdAndExpLessThanOrderByAcquiredAtAscIdAsc(
				childId, ChildCharacter.MAXIMUM_EXPERIENCE);
	}

	/**
	 * 도감 순서에서 아직 없는 다음 캐릭터를 지급한다. 줄 것이 없으면 null 이다.
	 *
	 * 순서가 코드 오름차순으로 정해져 있어 같은 입력이면 항상 같은 캐릭터가
	 * 나온다. 확률 추첨이 아니므로 테스트가 결과를 한 줄로 적을 수 있다.
	 */
	private ChildCharacter grantNextCharacter(Long childId) {
		List<Long> ownedCharacterIds =
			childCharacterRepository.findAllByChildIdOrderByAcquiredAtAscIdAsc(childId).stream()
				.map(ChildCharacter::getCharacterId)
				.toList();

		Optional<Character> nextCharacter = ownedCharacterIds.isEmpty()
			? characterRepository.findFirstByIsActiveTrueOrderByCodeAsc()
			: characterRepository.findFirstByIsActiveTrueAndIdNotInOrderByCodeAsc(ownedCharacterIds);

		return nextCharacter
			.map(character -> childCharacterRepository.save(
				ChildCharacter.grant(childId, character.getId(), clock.instant())))
			.orElse(null);
	}

	/**
	 * 보유 캐릭터에 딸린 도감 정보를 한 번에 읽는다.
	 *
	 * 마리마다 따로 물어보면 캐릭터 수만큼 질의가 나간다. 지금은 몇 마리
	 * 안 되지만 도감이 커지면 그대로 늘어난다.
	 */
	private Map<Long, Character> loadCharactersById(List<ChildCharacter> childCharacters) {
		if (childCharacters.isEmpty()) {
			return Map.of();
		}

		List<Long> characterIds = childCharacters.stream()
			.map(ChildCharacter::getCharacterId)
			.distinct()
			.toList();

		Map<Long, Character> charactersById = new HashMap<>();

		for (Character character : characterRepository.findAllById(characterIds)) {
			charactersById.put(character.getId(), character);
		}

		return charactersById;
	}

	/**
	 * S3 키가 담긴 JSON 문자열을 응답에 실을 수 있는 형태로 바꾼다.
	 *
	 * 서버는 이 안을 들여다보지 않는다. 키 구성(thumbnail, idle 등)이 디자인에
	 * 따라 바뀔 수 있어서, 형태를 코드로 고정하면 디자인이 바뀔 때마다 서버를
	 * 고쳐야 한다. 값이 비어 있으면 null 로 나간다.
	 */
	private JsonNode readAssets(Character character) {
		if (character.getAssets() == null || character.getAssets().isBlank()) {
			return null;
		}

		return objectMapper.readTree(character.getAssets());
	}

}
