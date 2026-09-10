package artistic.software.khu.artistic_software_khu.character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 자녀가 가진 캐릭터 한 마리. "ERD.md" 2장 CHILD_CHARACTERS 를 옮긴다.
 *
 * 성장 규칙이 전부 이 안에 있다("API.md" 12-1). 완료 1개당 경험치 1,
 * 10마다 레벨 1, 최대 레벨 3, 경험치는 30 에서 멈춘다.
 *
 * 규칙을 엔티티 안에 두는 이유는 경험치와 레벨이 항상 같이 움직여야 하기
 * 때문이다. 밖에서 각각 고치게 두면 경험치는 올랐는데 레벨은 그대로인
 * 상태를 만들 수 있고, 그런 행은 화면에서만 이상해 보일 뿐 오류로 잡히지 않는다.
 */
@Entity
@Table(name = "child_characters")
public class ChildCharacter {

	/** 한 마리를 다 키우는 데 드는 완료 미션 수. */
	public static final int MAXIMUM_EXPERIENCE = 30;

	/** 레벨 하나가 오르는 데 드는 경험치. */
	public static final int EXPERIENCE_PER_LEVEL = 10;

	/** 최대 레벨. 이 값에 닿으면 더 오르지 않는다. */
	public static final int MAXIMUM_LEVEL = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "child_id", nullable = false)
	private Long childId;

	@Column(name = "character_id", nullable = false)
	private Long characterId;

	@Column(name = "level", nullable = false)
	private int level;

	// experience (경험치) 를 줄인 이름이 아니라 "ERD.md" 의 컬럼명 그대로다.
	@Column(name = "exp", nullable = false)
	private int exp;

	@Column(name = "acquired_at", nullable = false)
	private Instant acquiredAt;

	protected ChildCharacter() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private ChildCharacter(Long childId, Long characterId, Instant acquiredAt) {
		this.childId = childId;
		this.characterId = characterId;
		this.level = 1;
		this.exp = 0;
		this.acquiredAt = acquiredAt;
	}

	/** 캐릭터 한 마리를 새로 받는다. 레벨 1, 경험치 0 에서 시작한다. */
	public static ChildCharacter grant(Long childId, Long characterId, Instant acquiredAt) {
		return new ChildCharacter(childId, characterId, acquiredAt);
	}

	/**
	 * 경험치를 넣고 "실제로 들어간 양" 을 돌려준다.
	 *
	 * 요청한 양을 다 넣지 못할 수 있다. 상한이 30 이기 때문이다. 돌려주는 값이
	 * 있어야 부르는 쪽이 "남은 경험치를 다음 캐릭터에게 넘길지" 를 판단할 수 있다.
	 */
	public int addExperience(int amount) {
		if (amount <= 0) {
			return 0;
		}

		int applied = Math.min(amount, MAXIMUM_EXPERIENCE - exp);

		this.exp += applied;
		this.level = Math.min(MAXIMUM_LEVEL, exp / EXPERIENCE_PER_LEVEL + 1);

		return applied;
	}

	/** 다 자랐는지. 다 자란 뒤에 들어온 경험치는 다음 캐릭터에게 간다. */
	public boolean isFullyGrown() {
		return exp >= MAXIMUM_EXPERIENCE;
	}

	public Long getId() {
		return id;
	}

	public Long getChildId() {
		return childId;
	}

	public Long getCharacterId() {
		return characterId;
	}

	public int getLevel() {
		return level;
	}

	public int getExp() {
		return exp;
	}

	public Instant getAcquiredAt() {
		return acquiredAt;
	}

}
