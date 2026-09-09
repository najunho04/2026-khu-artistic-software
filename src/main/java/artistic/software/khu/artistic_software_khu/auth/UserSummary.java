package artistic.software.khu.artistic_software_khu.auth;

import artistic.software.khu.artistic_software_khu.user.User;

/**
 * 인증 응답에 실리는 유저 정보. "API.md" 4장 응답의 "user" 부분이다.
 *
 * name 이 null 이면 앱은 온보딩 1차(보호자 성명 입력)로 분기한다.
 * 소셜 로그인 시절의 isFirstLogin 필드를 대신하는 신호다. 가입과 로그인이
 * 분리되면서 "이번이 첫 로그인인가" 를 서버가 따로 알려줄 이유가 없어졌다.
 */
public record UserSummary(Long userId, String name, String email) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getName(), user.getEmail());
	}

}
