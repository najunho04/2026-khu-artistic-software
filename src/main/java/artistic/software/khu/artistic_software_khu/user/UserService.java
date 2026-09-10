package artistic.software.khu.artistic_software_khu.user;

import artistic.software.khu.artistic_software_khu.child.Child;
import artistic.software.khu.artistic_software_khu.child.ChildRepository;
import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import artistic.software.khu.artistic_software_khu.routine.BigRoutineRepository;
import artistic.software.khu.artistic_software_khu.routine.RoutineTemplateRepository;
import artistic.software.khu.artistic_software_khu.routine.SmallRoutineRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 · 수정 · 탈퇴. "API.md" 5장.
 *
 * 탈퇴가 보호자 한 명이 아니라 그 아래 다섯 종류(자녀 · 기기 · 빅루틴 ·
 * 스몰루틴 · 양식)를 건드리기 때문에 이 서비스가 여러 도메인의 저장소를
 * 함께 들고 있다. 도메인마다 서비스를 거치지 않고 저장소를 바로 부르는
 * 이유는, 각 서비스의 삭제가 "그 자원 하나를 지우는" 동작이라 소유권 검사와
 * 응답 만들기가 딸려 오는데 탈퇴에는 그것이 필요 없기 때문이다. 이미 계정
 * 주인임이 확인된 상태에서 그 아래를 통째로 지우는 작업이다.
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	private final ChildRepository childRepository;

	private final DeviceRepository deviceRepository;

	private final BigRoutineRepository bigRoutineRepository;

	private final SmallRoutineRepository smallRoutineRepository;

	private final RoutineTemplateRepository routineTemplateRepository;

	private final Clock clock;

	public UserService(
		UserRepository userRepository,
		ChildRepository childRepository,
		DeviceRepository deviceRepository,
		BigRoutineRepository bigRoutineRepository,
		SmallRoutineRepository smallRoutineRepository,
		RoutineTemplateRepository routineTemplateRepository,
		Clock clock) {

		this.userRepository = userRepository;
		this.childRepository = childRepository;
		this.deviceRepository = deviceRepository;
		this.bigRoutineRepository = bigRoutineRepository;
		this.smallRoutineRepository = smallRoutineRepository;
		this.routineTemplateRepository = routineTemplateRepository;
		this.clock = clock;
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
	 * 회원 탈퇴. 보호자에게 딸린 것을 전부 함께 지운다("ERD.md" 5-7).
	 *
	 * 전부 soft delete 이므로 행은 남는다. 되돌릴 수 있다는 뜻이고, 공모전
	 * 기간에 정책이 바뀌어도 deleted_at 을 비우면 복구된다.
	 *
	 * 이 메서드 전체가 하나의 트랜잭션(하나의 작업 단위. 중간에 끊기면 통째로
	 * 되돌아감)이다. 계정만 지워지고 기기가 살아남는 중간 상태가 생기면 그
	 * 기기는 주인 없이 계속 인증에 성공한다.
	 */
	@Transactional
	public void withdraw(Long userId) {
		User user = findActiveUser(userId);

		Instant deletedAt = clock.instant();

		// 자녀를 먼저 읽어 둔다. 아래에서 자녀 행을 지우고 나면 자녀 id 를
		// 다시 구할 수 없어 기기와 루틴을 어디까지 지워야 하는지 알 수 없다.
		List<Child> children = childRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId);

		for (Child child : children) {
			// 스몰루틴을 빅루틴보다 먼저 지운다. 스몰루틴을 찾는 조회가
			// 빅루틴 테이블을 거쳐 가는데, 순서가 반대면 "이미 지운 빅루틴에
			// 매달린 할 일" 이라는 상태를 한 번 거치게 된다. 지금 쿼리는 그
			// 경우도 처리하지만, 순서를 지키면 그 처리에 기대지 않아도 된다.
			smallRoutineRepository.softDeleteAllByChildId(child.getId(), deletedAt);
			bigRoutineRepository.softDeleteAllByChildId(child.getId(), deletedAt);
			routineTemplateRepository.softDeleteAllByChildId(child.getId(), deletedAt);

			// 자녀 삭제가 이미 하는 일과 같은 메서드를 쓴다. 여기서만 따로
			// 기기를 지우면 "자녀 삭제 때는 되는데 탈퇴 때는 안 되는" 차이가
			// 생길 수 있다.
			deviceRepository.releaseAllByChildId(child.getId(), deletedAt);
		}

		childRepository.softDeleteAllByUserId(userId, deletedAt);

		// 계정은 맨 마지막에 지운다. 위의 벌크 UPDATE 들이 영속성 컨텍스트를
		// 비우기(clearAutomatically) 때문에, 먼저 바꿔 두면 그 변경이 DB 로
		// 내려가기 전에 사라진다.
		user = findActiveUser(userId);
		user.withdraw(deletedAt);
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
