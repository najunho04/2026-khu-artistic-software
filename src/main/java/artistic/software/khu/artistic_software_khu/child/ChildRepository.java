package artistic.software.khu.artistic_software_khu.child;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
