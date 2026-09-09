package artistic.software.khu.artistic_software_khu.user;

import java.time.Instant;

/**
 * 내 정보 응답. "API.md" 5장.
 *
 * 담지 않는 것이 담는 것만큼 중요하다. "password_hash" 는 당연하고,
 * "access_uuid" 도 넣지 않는다. 만료가 없어서 한 번 새어 나가면 회수할
 * 방법이 로그아웃뿐이기 때문이다.
 *
 * "provider" 도 뺐다(2026-09-09). 소셜 로그인을 없애면서 값이 항상 비게
 * 되었다. 컬럼은 나중을 위해 DB 에 남겨 두었지만 응답에 담을 이유가 없다.
 *
 * name 이 null 이면 앱은 아직 온보딩 1차를 마치지 않은 것으로 본다.
 */
public record UserResponse(Long userId, String name, String email, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
	}

}
