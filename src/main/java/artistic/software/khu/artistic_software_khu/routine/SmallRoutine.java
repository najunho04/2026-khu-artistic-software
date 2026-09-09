package artistic.software.khu.artistic_software_khu.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 빅루틴 안의 할 일. "ERD.md" 1장 SMALL_ROUTINES.
 *
 * 이행률의 분모와 분자가 모두 이 테이블에서 나온다. 그래서 이 행의 개수를
 * 함부로 바꾸면 지나간 날의 성적이 소급해서 달라진다("API.md" 9장).
 */
@Entity
@Table(name = "small_routines")
public class SmallRoutine {

	// "API.md" 9장의 status 값. 완료 여부만 있으면 되므로 두 개뿐이다.
	public static final String STATUS_PENDING = "PENDING";

	public static final String STATUS_DONE = "DONE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "big_routine_id", nullable = false)
	private Long bigRoutineId;

	// 같은 할 일이 여러 날짜에 걸쳐 있을 때 하나로 묶는 값.
	// "양치하기 미션의 이행률" 같은 집계가 이 값을 기준으로 한다.
	@Column(name = "series_id")
	private UUID seriesId;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "sort_order")
	private Integer sortOrder;

	@Column(name = "status")
	private String status;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected SmallRoutine() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private SmallRoutine(Long bigRoutineId, UUID seriesId, String title, Integer sortOrder) {
		this.bigRoutineId = bigRoutineId;
		this.seriesId = seriesId;
		this.title = title;
		this.sortOrder = sortOrder;
		this.status = STATUS_PENDING;
	}

	public static SmallRoutine create(
		Long bigRoutineId, UUID seriesId, String title, Integer sortOrder) {

		return new SmallRoutine(bigRoutineId, seriesId, title, sortOrder);
	}

	/**
	 * 제목만 바꾼다. seriesId 는 건드리지 않는다.
	 *
	 * "ROADMAP.md" 3-3 이 정한 규칙이다. 제목이 바뀌어도 "원래 같은 미션" 이라는
	 * 사실은 그대로이므로, 이름을 고쳤다고 통계가 두 갈래로 갈라지면 안 된다.
	 */
	public void updateTitle(String title) {
		if (title != null) {
			this.title = title;
		}
	}

	/**
	 * 완료 상태를 반영한다. 기기 동기화가 부른다.
	 *
	 * 같은 값을 다시 넣어도 결과가 같다. 이것이 멱등성의 실체다. 기기가
	 * 네트워크 실패로 같은 기록을 재전송해도 행이 늘거나 값이 어긋나지 않는다.
	 *
	 * 되돌리는 경우(DONE -> PENDING)도 허용한다. 아이가 실수로 눌렀다가
	 * 취소하는 일이 실제로 일어난다. 그때 완료 시각도 함께 비운다.
	 */
	public void applyCompletion(String status, Instant completedAt) {
		if (STATUS_DONE.equals(status)) {
			this.status = STATUS_DONE;
			this.completedAt = completedAt;
			return;
		}

		this.status = STATUS_PENDING;
		this.completedAt = null;
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public void delete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getBigRoutineId() {
		return bigRoutineId;
	}

	public UUID getSeriesId() {
		return seriesId;
	}

	public String getTitle() {
		return title;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public String getStatus() {
		return status;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
