package artistic.software.khu.artistic_software_khu.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 자녀 프로필. "ERD.md" 1장 CHILDREN 을 옮긴다.
 *
 * "userId" 를 연관관계(@ManyToOne)가 아니라 값으로 들고 있는 이유는, 이 값이
 * 쓰이는 곳이 "이 자녀가 그 보호자의 것인가" 하나뿐이기 때문이다. 그 판정에는
 * id 만 있으면 되고, 연관관계로 두면 자녀를 읽을 때마다 보호자까지 딸려 오거나
 * 지연 로딩 시점을 신경 써야 한다.
 */
@Entity
@Table(name = "children")
public class Child {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	// 문자열로 저장한다. 순서(ORDINAL)로 저장하면 나중에 열거형 가운데에 값을
	// 하나 끼워 넣는 순간 이미 저장된 모든 행의 뜻이 조용히 바뀐다.
	@Enumerated(EnumType.STRING)
	@Column(name = "relationship")
	private Relationship relationship;

	// soft delete. 값이 차 있으면 삭제된 자녀다.
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Child() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private Child(Long userId, String name, LocalDate birthDate, Relationship relationship) {
		this.userId = userId;
		this.name = name;
		this.birthDate = birthDate;
		this.relationship = relationship;
	}

	public static Child register(
		Long userId, String name, LocalDate birthDate, Relationship relationship) {

		return new Child(userId, name, birthDate, relationship);
	}

	/**
	 * 보낸 값만 바꾼다. null 은 "바꾸지 않는다" 는 뜻이다.
	 *
	 * null 을 그대로 덮어쓰면 이름만 고치려던 사용자가 생년월일을 잃는다.
	 * "API.md" 6장이 PATCH 의 세 필드를 모두 선택으로 두었기 때문에
	 * 이 구분이 반드시 필요하다.
	 */
	public void update(String name, LocalDate birthDate, Relationship relationship) {
		if (name != null) {
			this.name = name;
		}

		if (birthDate != null) {
			this.birthDate = birthDate;
		}

		if (relationship != null) {
			this.relationship = relationship;
		}
	}

	public void delete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	/**
	 * 이 자녀가 그 보호자의 것인지 확인한다.
	 *
	 * 판정을 엔티티 안에 두는 이유는, 이 규칙이 자녀를 다루는 모든 곳에서
	 * 똑같이 쓰이기 때문이다. 서비스마다 "userId 를 비교" 하는 코드를 손으로
	 * 쓰면 한 곳에서 빠뜨렸을 때 그 경로만 남의 자녀가 열린다.
	 */
	public boolean isOwnedBy(Long userId) {
		return this.userId.equals(userId);
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public Relationship getRelationship() {
		return relationship;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
