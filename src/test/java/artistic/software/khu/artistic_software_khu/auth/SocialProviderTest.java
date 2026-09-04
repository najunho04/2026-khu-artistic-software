package artistic.software.khu.artistic_software_khu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지원하는 소셜 로그인 제공자 목록을 고정한다.
 *
 * 2026-09-04 에 소셜 로그인을 구글로 제한하기로 확정했다. 카카오는 구현 범위에서
 * 빠졌다. 다만 USERS.provider 컬럼과 이 열거형 자체는 남겨둔다. 제공자가 하나라고
 * 구조를 없애면 나중에 하나 더 붙일 때 이미 쌓인 데이터를 손봐야 하기 때문이다.
 */
class SocialProviderTest {

	@Test
	@DisplayName("지원하는 제공자는 구글 하나뿐이다")
	void onlyGoogleIsSupported() {
		assertThat(SocialProvider.values()).containsExactly(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("GOOGLE 문자열은 제공자로 해석된다")
	void googleIsResolvedFromRequestValue() {
		assertThat(SocialProvider.from("GOOGLE")).contains(SocialProvider.GOOGLE);
	}

	@Test
	@DisplayName("지원하지 않는 값은 비어 있는 결과가 된다")
	void unsupportedValueIsNotResolved() {
		// 여기서 예외를 던지지 않고 빈 결과를 돌려주는 이유는, 이 값을 받아
		// AUTH_INVALID_PROVIDER 400 으로 바꾸는 일이 호출하는 쪽의 몫이기 때문이다.
		// 카카오는 지금 지원하지 않으므로 이 경우에 해당한다.
		assertThat(SocialProvider.from("KAKAO")).isEmpty();
		assertThat(SocialProvider.from("네이버")).isEmpty();
		assertThat(SocialProvider.from(null)).isEmpty();
	}

	@Test
	@DisplayName("소문자로 와도 해석하지 않는다")
	void lowercaseValueIsNotResolved() {
		// "API.md" 가 enum 값을 대문자로 정의했다. 관대하게 받아주면 앱과 서버가
		// 서로 다른 표기를 쓰게 되고, 어느 쪽이 맞는지 알 수 없어진다.
		assertThat(SocialProvider.from("google")).isEmpty();
	}

	@Test
	@DisplayName("Optional 로 돌려주어 호출하는 쪽이 분기할 수 있다")
	void resolutionResultIsOptional() {
		Optional<SocialProvider> resolved = SocialProvider.from("GOOGLE");
		assertThat(resolved).isPresent();
	}

}
