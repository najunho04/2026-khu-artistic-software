package artistic.software.khu.artistic_software_khu.auth;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import artistic.software.khu.artistic_software_khu.user.User;
import artistic.software.khu.artistic_software_khu.user.UserRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 · 로그인 · 로그아웃. "API.md" 4장.
 */
@Service
public class AuthenticationService {

	// 이메일 형식 검사. 실제로 도달하는 주소인지는 확인하지 않는다.
	// 확인하려면 메일을 보내야 하는데 그 수단이 로드맵에 없다. ("API.md" 4장)
	//
	// 골뱅이 앞뒤에 공백이 없는 글자가 있고 점 뒤에 글자가 더 있는지만 본다.
	// 표준 규격을 그대로 옮기지 않는 이유는 그 규격이 실제로는 거의 모든 문자열을
	// 허용해서 검사의 의미가 없어지기 때문이다.
	private static final Pattern EMAIL_PATTERN =
		Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	// "API.md" 15장에서 확정한 최소 길이. 문자 조합 규칙은 두지 않는다.
	// 조합 규칙은 기억하기 어려운 비밀번호를 만들게 해 오히려 다른 곳에서 쓰던
	// 것을 재사용하게 만든다. 길이가 조합보다 효과가 크다.
	private static final int MINIMUM_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * 가입과 동시에 로그인 상태가 된다. 가입 직후 다시 로그인시키는 화면이 필요 없다.
	 */
	@Transactional
	public AuthenticationResponse signUp(SignUpRequest request) {
		validateEmailFormat(request.email());
		validatePasswordLength(request.password());

		// 먼저 확인하는 것은 사용자에게 제대로 된 메시지를 주기 위해서다.
		// 다만 이 확인과 저장 사이에 다른 요청이 끼어들 수 있으므로
		// 이것만으로는 중복을 막지 못한다. 마지막 방어선은 아래의 DB 유니크 제약이다.
		if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		User user = User.signUp(request.email(), passwordEncoder.encode(request.password()));
		UUID accessUuid = user.issueAccessUuid();

		try {
			// flush 를 명시적으로 부르는 이유는 유니크 제약 위반을 "여기서" 잡기
			// 위해서다. 그냥 두면 트랜잭션이 끝나는 시점에 터지는데, 그때는 이미
			// 이 try 블록을 벗어나 있어 409 로 바꿔줄 수 없다.
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			// 확인과 저장 사이에 같은 이메일이 들어온 경우다.
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		return AuthenticationResponse.of(accessUuid, user);
	}

	/**
	 * 로그인할 때마다 접속 값을 새로 발급하고 기존 값을 덮어쓴다.
	 */
	@Transactional
	public AuthenticationResponse logIn(LogInRequest request) {
		// 두 값 중 하나라도 비어 있으면 비밀번호를 맞춰보기 "전에" 거절한다.
		// 비밀번호를 인코더에 그대로 넘기면 null 을 받은 인코더가 예외를 던져
		// 500 이 나가는데, 값을 빠뜨린 것은 서버 잘못이 아니다.
		// "API.md" 4장이 두 필드를 모두 필수로 두었다.
		validateCredentialsPresent(request);

		// 이메일이 없는 경우와 비밀번호가 틀린 경우를 "같은 예외" 로 처리한다.
		// 구분해 알려주면 "이 이메일은 가입되어 있다" 는 사실이 새어 나가고,
		// 공격자가 이메일 목록을 넣어보며 가입 여부를 알아낼 수 있게 된다.
		User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		UUID accessUuid = user.issueAccessUuid();

		return AuthenticationResponse.of(accessUuid, user);
	}

	/**
	 * 접속 값을 비운다. 만료가 없는 구조라 이것이 값을 무효로 만드는 유일한 수단이다.
	 */
	@Transactional
	public void logOut(Long userId) {
		userRepository.findById(userId).ifPresent(User::clearAccessUuid);
	}

	/**
	 * 로그인에 필요한 두 값이 들어왔는지 본다.
	 *
	 * 여기서는 이메일이 가입돼 있는지 보지 않으므로 "이 이메일은 있다" 는
	 * 사실이 새어 나가지 않는다. 값을 아예 안 보낸 것과 틀리게 보낸 것은
	 * 다른 문제이고, 앞엣것은 앱이 고쳐야 할 입력 오류다.
	 */
	private void validateCredentialsPresent(LogInRequest request) {
		boolean emailMissing = request.email() == null || request.email().isBlank();
		boolean passwordMissing = request.password() == null || request.password().isBlank();

		if (emailMissing || passwordMissing) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private void validateEmailFormat(String email) {
		if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_EMAIL_FORMAT);
		}
	}

	private void validatePasswordLength(String password) {
		if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

}
