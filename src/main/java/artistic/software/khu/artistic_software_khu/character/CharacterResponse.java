package artistic.software.khu.artistic_software_khu.character;

import tools.jackson.databind.JsonNode;

/**
 * 도감 한 마리의 응답. "API.md" 12장 GET /characters.
 *
 * "weight"(획득 가중치) 는 담지 않는다. 서버 내부용이고, 응답에 실리면 앱이
 * 그 값으로 무언가를 계산하기 시작할 수 있다.
 */
public record CharacterResponse(
	Long characterId,
	String code,
	String name,
	String rarity,
	JsonNode assets) {

	public static CharacterResponse of(Character character, JsonNode assets) {
		return new CharacterResponse(
			character.getId(),
			character.getCode(),
			character.getName(),
			character.getRarity(),
			assets);
	}

}
