package artistic.software.khu.artistic_software_khu.routine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 빅루틴 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 *
 * 시리즈 전체를 다루는 메서드에는 "routineDate >= :fromDate" 도 함께 붙는다.
 * 오늘보다 이전 날짜를 건드리지 않기 위해서다("API.md" 9장). 지난 기록은
 * 그때 실제로 무엇을 하기로 했었는지를 담고 있어야 한다.
 */
public interface BigRoutineRepository extends JpaRepository<BigRoutine, Long> {

	Optional<BigRoutine> findByIdAndDeletedAtIsNull(Long id);

	List<BigRoutine> findAllByChildIdAndRoutineDateBetweenAndDeletedAtIsNullOrderByRoutineDateAscIdAsc(
		Long childId, LocalDate fromDate, LocalDate toDate);

	/**
	 * 같은 시리즈에서 "오늘 이후" 날짜의 행만 가져온다.
	 *
	 * 수정과 삭제가 모두 이 목록을 대상으로 한다. 과거를 빼는 이유는
	 * "API.md" 9장에 적힌 대로이고, 특히 스몰루틴을 과거에 더하면
	 * 이미 지나간 날의 이행률이 떨어지기 때문이다.
	 */
	List<BigRoutine> findAllBySeriesIdAndRoutineDateGreaterThanEqualAndDeletedAtIsNull(
		UUID seriesId, LocalDate fromDate);

}
