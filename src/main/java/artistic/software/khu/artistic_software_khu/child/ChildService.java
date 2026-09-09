package artistic.software.khu.artistic_software_khu.child;

import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 등록 · 조회 · 수정 · 삭제. "API.md" 6장.
 *
 * 이 클래스의 "findOwnedChild" 가 Phase 3 루틴 도메인 전체의 소유권 검사
 * 기반이 된다. 루틴 API 도 결국 "이 자녀가 내 자녀인가" 를 먼저 묻기 때문이다.
 */
@Service
public class ChildService {

	// "API.md" 3-3 이 확정한 보호자당 자녀 상한.
	private static final int MAXIMUM_CHILD_COUNT = 10;

	private final ChildRepository childRepository;

	private final DeviceRepository deviceRepository;

	// 시각을 직접 부르지 않고 주입받는 이유는 테스트에서 시각을 고정할 수 있게
	// 하기 위해서다. Instant.now() 를 코드 안에서 부르면 그 순간이 검증 불가능해진다.
	private final Clock clock;

	public ChildService(
		ChildRepository childRepository, DeviceRepository deviceRepository, Clock clock) {

		this.childRepository = childRepository;
		this.deviceRepository = deviceRepository;
		this.clock = clock;
	}

	@Transactional
	public ChildResponse register(Long userId, ChildRequest request) {
		validateNameRequired(request.name());
		validateBirthDate(request.birthDate());

		Relationship relationship = parseRelationship(request.relationship());

		if (relationship == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		// 삭제된 자녀는 세지 않는다. 세지 않아야 지운 뒤 자리가 난다.
		if (childRepository.countByUserIdAndDeletedAtIsNull(userId) >= MAXIMUM_CHILD_COUNT) {
			throw new BusinessException(ErrorCode.CHILD_LIMIT_EXCEEDED);
		}

		Child child = Child.register(
			userId, request.name(), request.birthDate(), relationship);

		return ChildResponse.from(childRepository.save(child));
	}

	@Transactional(readOnly = true)
	public List<ChildResponse> findMyChildren(Long userId) {
		return childRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId).stream()
			.map(ChildResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ChildResponse findOne(Long userId, Long childId) {
		return ChildResponse.from(findOwnedChild(userId, childId));
	}

	@Transactional
	public ChildResponse update(Long userId, Long childId, ChildRequest request) {
		Child child = findOwnedChild(userId, childId);

		validateBirthDate(request.birthDate());

		Relationship relationship = null;

		if (request.relationship() != null) {
			relationship = parseRelationship(request.relationship());

			// 보내긴 했는데 정해진 값이 아닌 경우다. null 로 넘기면
			// "안 보냈다" 와 구분되지 않아 조용히 무시된다.
			if (relationship == null) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
		}

		child.update(request.name(), request.birthDate(), relationship);

		return ChildResponse.from(child);
	}

	@Transactional
	public void delete(Long userId, Long childId) {
		Child child = findOwnedChild(userId, childId);

		child.delete(clock.instant());

		// "API.md" 6장이 "연결된 기기의 페어링 해제가 함께 일어나야 한다" 고 적었다.
		// 자녀가 사라졌는데 기기가 살아 있으면 그 기기는 주인 없는 상태로
		// 계속 인증에 성공하고, 그 값으로 다른 요청을 보낼 수 있다.
		//
		// 같은 트랜잭션 안에서 처리하므로 자녀만 지워지고 기기가 남는 중간
		// 상태가 생기지 않는다.
		deviceRepository.releaseAllByChildId(childId, clock.instant());
	}

	/**
	 * 내 자녀 하나를 찾는다. Phase 3 루틴 도메인도 이 메서드를 통해 소유권을 확인한다.
	 *
	 * 없는 자녀와 남의 자녀를 다른 코드로 구분한다. 로그인한 사용자에게는 이
	 * 구분이 새어 나가도 문제가 없고, 앱이 "잘못된 요청" 과 "권한 없음" 을
	 * 다르게 안내할 수 있어야 하기 때문이다. ("API.md" 1-1-1)
	 */
	@Transactional(readOnly = true)
	public Child findOwnedChild(Long userId, Long childId) {
		Child child = childRepository.findByIdAndDeletedAtIsNull(childId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHILD_NOT_FOUND));

		if (!child.isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.CHILD_FORBIDDEN);
		}

		return child;
	}

	private void validateNameRequired(String name) {
		if (name == null || name.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private void validateBirthDate(LocalDate birthDate) {
		if (birthDate == null) {
			return;
		}

		// "API.md" 6장이 "미래 날짜 불가" 라고 적었다. 오늘은 KST 기준이다.
		if (birthDate.isAfter(LocalDate.now(clock))) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	/**
	 * 문자열을 열거형으로 바꾼다. 정해진 값이 아니면 null 을 돌려준다.
	 *
	 * 예외를 여기서 던지지 않는 이유는, 등록과 수정에서 "값이 없는 경우" 의
	 * 뜻이 다르기 때문이다. 등록에서는 오류이고 수정에서는 "바꾸지 않는다" 다.
	 */
	private Relationship parseRelationship(String value) {
		if (value == null) {
			return null;
		}

		try {
			return Relationship.valueOf(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

}
