package artistic.software.khu.artistic_software_khu.routine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 저장해둔 양식 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 */
public interface RoutineTemplateRepository extends JpaRepository<RoutineTemplate, Long> {

	Optional<RoutineTemplate> findByIdAndDeletedAtIsNull(Long id);

	List<RoutineTemplate> findAllByChildIdAndDeletedAtIsNullOrderByIdAsc(Long childId);

	/** 한 자녀의 살아 있는 양식을 모두 지운다. 회원 탈퇴와 함께 쓴다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RoutineTemplate routineTemplate set routineTemplate.deletedAt = :deletedAt"
		+ " where routineTemplate.childId = :childId and routineTemplate.deletedAt is null")
	int softDeleteAllByChildId(
		@Param("childId") Long childId, @Param("deletedAt") Instant deletedAt);

}
