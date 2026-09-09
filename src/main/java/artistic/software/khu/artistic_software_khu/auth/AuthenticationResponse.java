package artistic.software.khu.artistic_software_khu.auth;

import artistic.software.khu.artistic_software_khu.user.User;
import java.util.UUID;

/**
 * 가입 · 로그인 응답. "API.md" 4장.
 *
 * accessUuid 는 앱이 저장해 두었다가 이후 모든 요청의 "X-Access-Uuid" 헤더에
 * 담는 값이다. 만료가 없으므로 갱신 호출이 따로 없다.
 */
public record AuthenticationResponse(UUID accessUuid, UserSummary user) {

	public static AuthenticationResponse of(UUID accessUuid, User user) {
		return new AuthenticationResponse(accessUuid, UserSummary.from(user));
	}

}
