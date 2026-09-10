package artistic.software.khu.artistic_software_khu.routine;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * 한 시리즈 안에서 지금까지 쓴 가장 큰 순서 값을 구한다.
	 *
	 * 지운 할 일도 함께 센다("deletedAt is null" 조건이 없다). 이것이 이
	 * 질의의 요점이다. 스몰루틴의 seriesId 는 "빅루틴 시리즈 + 순서" 로
	 * 계산되므로, 지운 할 일의 번호를 다시 쓰면 그 자리에 있던 미션과 같은
	 * 식별자가 만들어져 미션별 통계가 한 줄로 합쳐진다.
	 *
	 * 한 날짜가 아니라 시리즈 전체에서 구하는 이유는, 할 일 삭제가 그 날짜
	 * 하나만 지우기 때문이다. 날짜마다 살아 있는 개수가 달라지므로 한 날짜만
	 * 보고 번호를 정하면 손대지 않은 날짜의 할 일과 겹친다.
	 */
	@Query("select max(smallRoutine.sortOrder) from SmallRoutine smallRoutine"
		+ " where smallRoutine.bigRoutineId in"
		+ " (select bigRoutine.id from BigRoutine bigRoutine"
		+ "  where bigRoutine.seriesId = :seriesId)")
	Integer findMaximumSortOrderBySeriesId(@Param("seriesId") UUID seriesId);

	/**
	 * 빅루틴 하나 안에서 지금까지 쓴 가장 큰 순서 값을 구한다.
	 *
	 * 시리즈 값이 없는 빅루틴을 위한 갈래다. 지금은 생성 경로가 항상 시리즈
	 * 값을 채우지만, 컬럼이 null 을 허용하고 있어(ERD 1장) 대비해 둔다.
	 */
	@Query("select max(smallRoutine.sortOrder) from SmallRoutine smallRoutine"
		+ " where smallRoutine.bigRoutineId = :bigRoutineId")
	Integer findMaximumSortOrderByBigRoutineId(@Param("bigRoutineId") Long bigRoutineId);

	/**
	 * 한 자녀의 살아 있는 할 일을 모두 지운다. 회원 탈퇴와 함께 쓴다.
	 *
	 * 스몰루틴은 자녀를 직접 가리키지 않고 빅루틴을 통해 매달려 있어서
	 * 빅루틴 id 를 먼저 읽어 와야 할 것 같지만, 안쪽 조회로 한 번에 처리한다.
	 * 한 달치 루틴이면 빅루틴만 수십 개라 id 목록을 애플리케이션까지
	 * 가져올 이유가 없다.
	 *
	 * 안쪽 조회에 "deletedAt is null" 을 걸지 않는 것은 일부러다. 이미 지운
	 * 빅루틴에 매달린 할 일도 함께 지워야 남는 것이 없다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update SmallRoutine smallRoutine set smallRoutine.deletedAt = :deletedAt"
		+ " where smallRoutine.deletedAt is null"
		+ " and smallRoutine.bigRoutineId in"
		+ " (select bigRoutine.id from BigRoutine bigRoutine where bigRoutine.childId = :childId)")
	int softDeleteAllByChildId(
		@Param("childId") Long childId, @Param("deletedAt") Instant deletedAt);

}
