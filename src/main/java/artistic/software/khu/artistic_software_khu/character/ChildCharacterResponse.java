package artistic.software.khu.artistic_software_khu.character;

import java.time.Instant;

/**
 * 자녀가 가진 캐릭터 하나의 응답. "API.md" 12장 GET /children/:childId/characters.
 *
 * 도감의 "code" 와 "name" 을 함께 담는다. 앱이 캐릭터 목록을 그리려고 도감을
 * 한 번 더 부르지 않게 하기 위해서다.
 */
public record ChildCharacterResponse(
	Long childCharacterId,
	Long characterId,
	String code,
	String name,
	int level,
	int exp,
	Instant acquiredAt) {

	public static ChildCharacterResponse of(ChildCharacter childCharacter, Character character) {
		return new ChildCharacterResponse(
			childCharacter.getId(),
			childCharacter.getCharacterId(),
			character == null ? null : character.getCode(),
			character == null ? null : character.getName(),
			childCharacter.getLevel(),
			childCharacter.getExp(),
			childCharacter.getAcquiredAt());
	}

}
