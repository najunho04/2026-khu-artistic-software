package artistic.software.khu.artistic_software_khu.routine;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
