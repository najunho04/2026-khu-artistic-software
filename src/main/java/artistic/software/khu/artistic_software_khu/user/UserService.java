package artistic.software.khu.artistic_software_khu.user;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 · 수정. "API.md" 5장.
 *
 * 탈퇴는 없다. cascade 정책이 미확정이라(0-3) 무엇을 함께 지울지 정하지 않은
 * 채 만들면 자녀와 기기와 루틴이 어중간하게 남는다.
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse findMe(Long userId) {
		return UserResponse.from(findActiveUser(userId));
	}

	@Transactional
	public UserResponse updateMe(Long userId, UserUpdateRequest request) {
		// 빈 값을 그대로 받으면 온보딩을 마친 것으로 보이는데 실제로는 이름이
		// 없는 상태가 된다. "API.md" 5장이 name 을 필수로 두었다.
		if (request.name() == null || request.name().isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		User user = findActiveUser(userId);
		user.changeName(request.name());

		return UserResponse.from(user);
	}

	/**
	 * 탈퇴하지 않은 유저를 찾는다.
	 *
	 * 인증 필터가 이미 같은 조건으로 찾아 통과시켰으므로 여기서 못 찾는 일은
	 * 사실상 없다. 그래도 확인하는 이유는 필터를 거치지 않는 경로가 나중에
	 * 생겼을 때 조용히 탈퇴한 계정을 다루지 않게 하기 위해서다.
	 */
	private User findActiveUser(Long userId) {
		return userRepository.findById(userId)
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

}
