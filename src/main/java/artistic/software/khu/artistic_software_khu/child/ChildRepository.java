package artistic.software.khu.artistic_software_khu.child;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
