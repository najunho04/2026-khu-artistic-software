package artistic.software.khu.artistic_software_khu.auth;

/**
 * 회원가입 요청. "API.md" 4장 POST /auth/signup.
 *
 * 검증 어노테이션을 쓰지 않고 서비스에서 직접 검사하는 이유는, 이메일 형식 오류가
 * 공통 코드 INVALID_INPUT 이 아니라 전용 코드 AUTH_INVALID_EMAIL_FORMAT 으로
 * 나가야 하기 때문이다. 어노테이션 검증은 전부 INVALID_INPUT 으로 묶인다.
 */
public record SignUpRequest(String email, String password) {
}
