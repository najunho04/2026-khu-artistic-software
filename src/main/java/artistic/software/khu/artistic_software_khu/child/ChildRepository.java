package artistic.software.khu.artistic_software_khu.child;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 자녀 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 *
 * 특히 개수를 세는 쿼리에서 이 조건이 빠지면 상한(10명) 계산이 틀어진다.
 * soft delete 라 지운 자녀의 행이 남아 있어서, 조건이 없으면 지워도 자리가
 * 나지 않아 사용자가 영영 새 자녀를 등록하지 못한다.
 */
public interface ChildRepository extends JpaRepository<Child, Long> {

	Optional<Child> findByIdAndDeletedAtIsNull(Long id);

	List<Child> findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(Long userId);

	/**
	 * 자녀 행을 "잠그고" 읽는다. 같은 자녀를 건드리는 요청을 한 줄로 세우는 데 쓴다.
	 *
	 * 잠금(다른 트랜잭션이 같은 행에 닿으면 앞의 것이 끝날 때까지 기다리게 하는
	 * 장치)이 없으면, 동시에 들어온 두 요청이 둘 다 "바뀌기 전" 값을 읽고 각자
	 * 판단한다. 기기 동기화에서는 같은 완료 기록이 두 번 새 완료로 세어져
	 * 캐릭터 경험치가 두 번 오르고, 페어링에서는 상한 검사가 둘 다 통과해
	 * 자녀당 10대를 넘긴다.
	 *
	 * 자녀 행을 잠그는 이유는 그것이 기기 · 루틴 · 캐릭터가 모두 매달린
	 * 공통의 윗단이기 때문이다. 자녀 한 명에 걸린 요청만 기다리므로 다른
	 * 자녀의 요청은 그대로 지나간다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select child from Child child"
		+ " where child.id = :childId and child.deletedAt is null")
	Optional<Child> findByIdAndDeletedAtIsNullForUpdate(@Param("childId") Long childId);

	int countByUserIdAndDeletedAtIsNull(Long userId);

	/**
	 * 한 보호자의 살아 있는 자녀를 모두 지운다. 회원 탈퇴와 함께 쓴다.
	 *
	 * "deletedAt is null" 조건을 거는 이유는 이미 지운 자녀의 삭제 시각을
	 * 덮어쓰지 않기 위해서다. 그 시각은 그 자녀가 언제 지워졌는지를 담고 있는
	 * 기록이고, 탈퇴한 날로 덮이면 그 기록이 사라진다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Child child set child.deletedAt = :deletedAt"
		+ " where child.userId = :userId and child.deletedAt is null")
	int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") Instant deletedAt);

}
