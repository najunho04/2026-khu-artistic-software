package artistic.software.khu.artistic_software_khu.routine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 저장해둔 양식 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 */
public interface RoutineTemplateRepository extends JpaRepository<RoutineTemplate, Long> {

	Optional<RoutineTemplate> findByIdAndDeletedAtIsNull(Long id);

	List<RoutineTemplate> findAllByChildIdAndDeletedAtIsNullOrderByIdAsc(Long childId);

}
