package artistic.software.khu.artistic_software_khu.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 날짜별 루틴. "ERD.md" 1장 BIG_ROUTINES.
 *
 * 반복 관련 컬럼이 하나도 없는 것이 이 엔티티의 특징이다. 반복 모드는 날짜를
 * 펼치는 데에만 쓰이고 행에는 남지 않는다("API.md" 9장). 파생 가능한 값을 따로
 * 저장하면 두 값이 어긋나는 순간이 반드시 오기 때문이다. 예를 들어 "WEEKLY" 로
 * 저장해 두었는데 사용자가 그중 하루를 지우면, 컬럼은 여전히 WEEKLY 지만
 * 실제 행은 요일 규칙과 맞지 않게 된다.
 *
 * "template_id" 도 없다. 양식을 꺼내 쓰는 순간 값이 복사되고 둘의 관계는
 * 거기서 끝난다. 컬럼이 없으면 "양식을 고치면 이미 만든 루틴도 바뀌게"
 * 구현할 자리 자체가 사라진다.
 */
@Entity
@Table(name = "big_routines")
public class BigRoutine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "child_id", nullable = false)
	private Long childId;

	// 반복으로 만들어진 루틴들을 하나로 묶는 값. 수정·삭제 범위의 기준이자
	// 미션별 이행률 집계의 기준이다.
	@Column(name = "series_id")
	private UUID seriesId;

	@Column(name = "routine_date", nullable = false)
	private LocalDate routineDate;

	@Column(name = "title", nullable = false)
	private String title;

	// KST 벽시계 시각으로 해석한다("ERD.md" 5-2). 아침 8시 30분에 일어난다는
	// 약속은 시간대를 옮겨도 아침 8시 30분이어야 한다.
	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "sort_order")
	private Integer sortOrder;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected BigRoutine() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private BigRoutine(
		Long childId, UUID seriesId, LocalDate routineDate,
		String title, LocalTime startTime, LocalTime endTime) {

		this.childId = childId;
		this.seriesId = seriesId;
		this.routineDate = routineDate;
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	/**
	 * 계획의 한 날짜를 행으로 만든다.
	 *
	 * seriesId 를 계획에서 그대로 가져오는 것이 핵심이다. 여기서 새로 만들면
	 * 날짜마다 다른 값이 붙어 시리즈가 쪼개지고, 미션별 통계가 조용히 무너진다.
	 */
	public static BigRoutine from(
		Long childId, BigRoutineCreationPlan plan, LocalDate routineDate) {

		return new BigRoutine(
			childId, plan.seriesId(), routineDate,
			plan.title(), plan.startTime(), plan.endTime());
	}

	/**
	 * 보낸 값만 바꾼다. null 은 "바꾸지 않는다" 는 뜻이다.
	 */
	public void update(String title, LocalTime startTime, LocalTime endTime) {
		if (title != null) {
			this.title = title;
		}

		if (startTime != null) {
			this.startTime = startTime;
		}

		if (endTime != null) {
			this.endTime = endTime;
		}
	}

	public void delete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getChildId() {
		return childId;
	}

	public UUID getSeriesId() {
		return seriesId;
	}

	public LocalDate getRoutineDate() {
		return routineDate;
	}

	public String getTitle() {
		return title;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
