package artistic.software.khu.artistic_software_khu.routine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스몰루틴 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 */
public interface SmallRoutineRepository extends JpaRepository<SmallRoutine, Long> {

	Optional<SmallRoutine> findByIdAndDeletedAtIsNull(Long id);

	List<SmallRoutine> findAllByBigRoutineIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
		Long bigRoutineId);

	/**
	 * 여러 빅루틴의 할 일을 한 번에 가져온다. 캘린더 조회에서 쓴다.
	 *
	 * 빅루틴마다 따로 물어보면 한 달치 조회에 수십 번의 질의가 나간다.
	 * 한 번에 가져와 애플리케이션에서 묶는 편이 훨씬 싸다.
	 */
	List<SmallRoutine> findAllByBigRoutineIdInAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
		Collection<Long> bigRoutineIds);

}
