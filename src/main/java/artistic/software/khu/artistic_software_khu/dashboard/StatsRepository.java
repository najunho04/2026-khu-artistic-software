package artistic.software.khu.artistic_software_khu.dashboard;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import artistic.software.khu.artistic_software_khu.routine.SmallRoutine;

/**
 * 이행률 집계. "API.md" 11-1.
 *
 * 집계 테이블을 두지 않고 SMALL_ROUTINES 를 그때그때 센다. 자녀 한 명의 한 달치
 * 할 일이 많아야 수백 행이라 미리 계산해 둘 이유가 없고, 집계 테이블은 원본과
 * 어긋날 수 있다는 위험이 늘 따라붙는다.
 *
 * **두 테이블의 deleted_at 을 모두 본다.** 할 일만 지운 경우와 빅루틴째 지운
 * 경우가 둘 다 있기 때문이다. 한쪽만 보면 지운 루틴의 할 일이 분모에 남는다.
 *
 * 네이티브 쿼리를 쓴 이유는 "filter (where ...)" 와 "array_agg(... order by ...)"
 * 가 PostgreSQL 기능이라 JPQL 로 표현되지 않기 때문이다. DBMS 를 PostgreSQL 로
 * 고정했으므로("ERD.md") 이식성은 문제가 되지 않는다.
 */
public interface StatsRepository extends JpaRepository<SmallRoutine, Long> {

	/**
	 * 기간 전체의 할 일 수와 완료 수를 한 번에 센다.
	 *
	 * 두 번 질의하지 않는 이유는 그 사이에 값이 바뀌면 분자가 분모보다 커지는
	 * 일이 생길 수 있기 때문이다.
	 *
	 * @return [totalCount, doneCount] 한 행
	 */
	@Query(value = """
		select count(*) as total_count,
		       count(*) filter (where small.status = 'DONE') as done_count
		  from small_routines small
		  join big_routines big on big.id = small.big_routine_id
		 where big.child_id = :childId
		   and big.routine_date between :fromDate and :toDate
		   and small.deleted_at is null
		   and big.deleted_at is null
		""", nativeQuery = true)
	CountProjection countIn(
		@Param("childId") Long childId,
		@Param("fromDate") LocalDate fromDate,
		@Param("toDate") LocalDate toDate);

	/**
	 * series_id 로 묶어 미션별로 센다.
	 *
	 * title 은 "array_agg(... order by routine_date desc)[1]" 로 가장 최근 값을
	 * 고른다. 이름이 바뀐 미션은 series_id 가 유지되어 한 행으로 묶이는데,
	 * 그때 어느 이름을 보여줄지 정해야 하고 최근 것이 사용자가 방금 고친 값이다.
	 *
	 * 정렬은 이행률이 낮은 순이다. 보호자가 보려는 것이 "무엇이 잘 안 되고
	 * 있나" 이기 때문이다.
	 */
	@Query(value = """
		select small.series_id as series_id,
		       (array_agg(small.title order by big.routine_date desc, small.id desc))[1]
		           as title,
		       count(*) as total_count,
		       count(*) filter (where small.status = 'DONE') as done_count
		  from small_routines small
		  join big_routines big on big.id = small.big_routine_id
		 where big.child_id = :childId
		   and big.routine_date between :fromDate and :toDate
		   and small.deleted_at is null
		   and big.deleted_at is null
		 group by small.series_id
		 order by (count(*) filter (where small.status = 'DONE'))::numeric
		          / nullif(count(*), 0) asc,
		          title asc
		""", nativeQuery = true)
	List<MissionProjection> countByMission(
		@Param("childId") Long childId,
		@Param("fromDate") LocalDate fromDate,
		@Param("toDate") LocalDate toDate);

	/**
	 * 기간 전체 집계 결과. 스프링이 인터페이스를 보고 결과를 매핑한다.
	 */
	interface CountProjection {

		int getTotalCount();

		int getDoneCount();

	}

	/**
	 * 미션 하나의 집계 결과.
	 */
	interface MissionProjection {

		java.util.UUID getSeriesId();

		String getTitle();

		int getTotalCount();

		int getDoneCount();

	}

}
