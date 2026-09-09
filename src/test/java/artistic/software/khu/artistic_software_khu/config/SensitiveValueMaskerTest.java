package artistic.software.khu.artistic_software_khu.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 로그에 남기면 안 되는 값을 가리는 규칙. "API.md" 15장에서 확정한 방식이다.
 *
 * 이 규칙을 별도 조각으로 떼어 두는 이유는, 여기서 정하는 것이 "무엇을 어떻게
 * 가리는가" 하나뿐이기 때문이다. HTTP 요청을 흉내 내지 않고도 전부 검증할 수 있고,
 * 나중에 로그를 남기는 자리가 늘어나도 규칙은 이 한 곳만 고치면 된다.
 *
 * 가리는 방식이 값에 따라 다른 것이 이 테스트의 핵심이다. 한 가지 방식으로
 * 통일하면 둘 중 하나가 반드시 잘못된다. 전부 가리면 장애를 쫓을 수 없고,
 * 전부 일부만 남기면 6자리 페어링 코드는 사실상 통째로 드러난다.
 */
class SensitiveValueMaskerTest {

	private final SensitiveValueMasker masker = new SensitiveValueMasker();

	@Test
	@DisplayName("UUID 는 앞뒤 세 글자만 남기고 가운데를 가린다")
	void uuidKeepsFirstAndLastThreeCharacters() {
		String masked = masker.mask("accessUuid", "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d");

		// 앞뒤를 남기는 이유는 장애를 쫓을 때 "어느 요청들이 한 사람의 것인가" 를
		// 알아야 하기 때문이다. 가운데가 없으면 그 값으로 인증할 수는 없다.
		assertThat(masked).isEqualTo("3f2...c4d");
	}

	@ParameterizedTest
	@DisplayName("UUID 로 취급하는 필드 이름들")
	@ValueSource(strings = {"accessUuid", "deviceAccessUuid", "access_uuid", "device_access_uuid"})
	void uuidFieldNamesAreRecognized(String fieldName) {
		// camelCase 와 snake_case 를 모두 알아본다. 앞엣것은 API 필드 이름이고
		// 뒤엣것은 DB 컬럼 이름인데, 로그에는 둘 다 나올 수 있다. ("API.md" 1-2)
		String masked = masker.mask(fieldName, "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d");

		assertThat(masked).isEqualTo("3f2...c4d");
	}

	@ParameterizedTest
	@DisplayName("비밀번호와 페어링 코드는 통째로 가린다")
	@ValueSource(strings = {"password", "pairingCode", "pairing_code", "passwordHash", "password_hash"})
	void passwordAndPairingCodeAreFullyMasked(String fieldName) {
		String masked = masker.mask(fieldName, "myS3cretValue");

		assertThat(masked).isEqualTo("***");
	}

	@Test
	@DisplayName("여섯 자리 페어링 코드는 한 글자도 남지 않는다")
	void sixDigitPairingCodeLeavesNothing() {
		// 앞뒤 세 글자 방식을 그대로 쓰면 "482...913" 이 되어 전부 드러난다.
		// 이것이 값마다 방식을 나눈 이유다.
		String masked = masker.mask("pairingCode", "482913");

		assertThat(masked).isEqualTo("***").doesNotContain("482").doesNotContain("913");
	}

	@Test
	@DisplayName("가려야 할 값이 아니면 그대로 둔다")
	void nonSensitiveFieldIsUnchanged() {
		assertThat(masker.mask("email", "parent@example.com")).isEqualTo("parent@example.com");
		assertThat(masker.mask("title", "아침 준비")).isEqualTo("아침 준비");
	}

	@Test
	@DisplayName("필드 이름의 대소문자는 가리지 않는다")
	void fieldNameMatchingIgnoresCase() {
		// 헤더 이름은 대소문자가 뒤섞여 오는 일이 흔하다.
		assertThat(masker.mask("PASSWORD", "secret")).isEqualTo("***");
		assertThat(masker.mask("AccessUuid", "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d"))
			.isEqualTo("3f2...c4d");
	}

	@Test
	@DisplayName("짧은 값에 앞뒤 세 글자를 남기려다 값이 다 드러나지 않는다")
	void shortValueIsFullyMaskedInsteadOfPartially() {
		// 여섯 글자 이하면 앞 3 + 뒤 3 이 값 전체가 된다. 그럴 때는 부분 가리기를
		// 포기하고 통째로 가린다. 가리는 시늉만 하고 실제로는 다 보여주는 것이
		// 가장 나쁘다. 아무도 가려졌다고 믿고 확인하지 않기 때문이다.
		assertThat(masker.mask("accessUuid", "abcdef")).isEqualTo("***");
		assertThat(masker.mask("accessUuid", "abc")).isEqualTo("***");
	}

	@Test
	@DisplayName("일곱 글자부터 앞뒤 세 글자를 남긴다")
	void sevenCharacterValueKeepsBothEnds() {
		// 경계에서 한 글자 차이로 갈리는 실수를 막는다.
		// 일곱 글자면 가운데 한 글자가 가려지므로 값 전체가 드러나지 않는다.
		assertThat(masker.mask("accessUuid", "abcdefg")).isEqualTo("abc...efg");
	}

	@Test
	@DisplayName("값이 비어 있거나 없으면 그대로 둔다")
	void nullOrBlankValueIsUnchanged() {
		// 없는 값을 "***" 로 바꾸면 로그를 읽는 사람이 "무언가 들어 있었는데
		// 가려졌다" 고 오해한다. 실제로는 아무것도 오지 않은 것이다.
		assertThat(masker.mask("password", null)).isNull();
		assertThat(masker.mask("password", "")).isEmpty();
	}

}
