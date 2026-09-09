package artistic.software.khu.artistic_software_khu.auth;

/**
 * 로그인 요청. "API.md" 4장 POST /auth/login.
 */
public record LogInRequest(String email, String password) {
}
