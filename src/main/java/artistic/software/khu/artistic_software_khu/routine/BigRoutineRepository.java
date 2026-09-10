package artistic.software.khu.artistic_software_khu.routine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	/**
	 * 지정한 날짜들의 루틴만 읽는다. 기기 동기화가 쓴다.
	 *
	 * 기간(between)이 아니라 목록(in)인 이유는 기기가 연속되지 않은 날짜를
	 * 요청할 수 있기 때문이다. 오프라인이었던 날들을 골라 받아 가는 경우다.
	 */
	List<BigRoutine> findAllByChildIdAndRoutineDateInAndDeletedAtIsNullOrderByRoutineDateAscIdAsc(
		Long childId, Collection<LocalDate> routineDates);

	List<BigRoutine> findAllBySeriesIdAndRoutineDateGreaterThanEqualAndDeletedAtIsNull(
		UUID seriesId, LocalDate fromDate);

	/**
	 * 한 자녀의 살아 있는 빅루틴을 모두 지운다. 회원 탈퇴와 함께 쓴다.
	 *
	 * 날짜 조건을 걸지 않는다. 수정과 삭제는 "오늘 이후" 만 건드리지만
	 * ("API.md" 9장) 탈퇴는 계정을 통째로 없애는 것이라 지난 날짜의 루틴도
	 * 남길 이유가 없다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update BigRoutine bigRoutine set bigRoutine.deletedAt = :deletedAt"
		+ " where bigRoutine.childId = :childId and bigRoutine.deletedAt is null")
	int softDeleteAllByChildId(
		@Param("childId") Long childId, @Param("deletedAt") Instant deletedAt);

}
