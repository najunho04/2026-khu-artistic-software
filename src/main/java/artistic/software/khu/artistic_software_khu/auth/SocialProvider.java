package artistic.software.khu.artistic_software_khu.auth;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지원하는 소셜 로그인 제공자.
 *
 * 2026-09-04 에 소셜 로그인을 "구글로 제한" 하기로 확정해서 값이 하나뿐이다.
 * 카카오는 구현 범위에서 빠졌다.
 *
 * 값이 하나인데도 열거형을 두는 이유는 두 가지다. 첫째, USERS 테이블에
 * provider 컬럼이 있고 "unique(provider, provider_user_id)" 제약이 그 값을
 * 쓴다. 둘째, 나중에 제공자를 하나 더 붙일 때 여기에 값을 늘리고 검증기 구현체를
 * 하나 더 만들면 끝나게 하기 위해서다. 지금 구조를 없애면 그때 이미 쌓인
 * 데이터까지 손봐야 한다.
 */
public enum SocialProvider {

	GOOGLE;

	/**
	 * 요청으로 들어온 문자열을 제공자로 해석한다.
	 *
	 * 예외를 던지지 않고 빈 결과를 돌려주는 이유는, 이것을
	 * AUTH_INVALID_PROVIDER 400 으로 바꿀지 다르게 다룰지가 호출하는 쪽의
	 * 판단이기 때문이다. 지원하지 않는 값은 잘못된 요청이지 예외 상황이 아니다.
	 *
	 * 대소문자를 가리는 것도 의도한 것이다. "API.md" 가 enum 값을 대문자로
	 * 정의했는데 소문자까지 받아주면 앱과 서버가 서로 다른 표기를 쓰게 되고,
	 * 나중에는 어느 쪽이 맞는 표기인지 아무도 모르게 된다.
	 */
	public static Optional<SocialProvider> from(String requestValue) {
		return Arrays.stream(values())
			.filter(provider -> provider.name().equals(requestValue))
			.findFirst();
	}

}
