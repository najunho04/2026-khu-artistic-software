package artistic.software.khu.artistic_software_khu.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 저장해둔 루틴 양식. "ERD.md" 1장 ROUTINE_TEMPLATES.
 *
 * 자주 쓰는 루틴을 한 번 만들어 두고 나중에 꺼내 쓰는 용도다.
 * **자동으로 빅루틴을 만들지 않는다.** 반복 생성은 빅루틴 쪽이 직접 처리한다.
 *
 * 양식을 고쳐도 이미 만들어진 빅루틴은 바뀌지 않는다. 꺼내 쓰는 순간 값이
 * 복사되고 둘의 관계는 거기서 끝나기 때문이다. 워드의 서식 파일을 고쳐도
 * 이미 만든 문서는 그대로인 것과 같다.
 *
 * "small_routines" 는 jsonb 한 칸에 담는다. 이 값은 빅루틴을 만들 때 통째로
 * 복사되는 원본이라 따로 조회하거나 수정할 일이 없기 때문이다.
 */
@Entity
@Table(name = "routine_templates")
public class RoutineTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "child_id", nullable = false)
	private Long childId;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	// jsonb 컬럼을 문자열로 다룬다. 안을 들여다볼 일이 없고 통째로 넣고 빼기만
	// 하므로, 전용 타입을 만드는 것보다 이 편이 단순하다.
	//
	// "@JdbcTypeCode(SqlTypes.JSON)" 가 반드시 필요하다. 이것이 없으면
	// 하이버네이트가 이 필드를 평범한 varchar 로 보내고, PostgreSQL 이
	// "column is of type jsonb but expression is of type character varying"
	// 이라며 거절한다. 자바 쪽 타입이 String 이어도 DB 에는 JSON 으로
	// 보내야 한다는 것을 따로 알려주어야 한다.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "small_routines")
	private String smallRoutines;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected RoutineTemplate() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private RoutineTemplate(
		Long childId, String title, LocalTime startTime, LocalTime endTime, String smallRoutines) {

		this.childId = childId;
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.smallRoutines = smallRoutines;
	}

	public static RoutineTemplate save(
		Long childId, String title, LocalTime startTime, LocalTime endTime, String smallRoutines) {

		return new RoutineTemplate(childId, title, startTime, endTime, smallRoutines);
	}

	/**
	 * 보낸 값만 바꾼다. 이 수정은 이미 만들어진 빅루틴에 전파되지 않는다.
	 */
	public void update(
		String title, LocalTime startTime, LocalTime endTime, String smallRoutines) {

		if (title != null) {
			this.title = title;
		}

		if (startTime != null) {
			this.startTime = startTime;
		}

		if (endTime != null) {
			this.endTime = endTime;
		}

		if (smallRoutines != null) {
			this.smallRoutines = smallRoutines;
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

	public String getTitle() {
		return title;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public String getSmallRoutines() {
		return smallRoutines;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
