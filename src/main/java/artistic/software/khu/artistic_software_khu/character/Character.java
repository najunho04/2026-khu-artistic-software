package artistic.software.khu.artistic_software_khu.character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 캐릭터 도감의 한 마리. "ERD.md" 2장 CHARACTERS 를 옮긴다.
 *
 * "weight"(획득 가중치) 컬럼은 매핑하지 않는다. 획득이 확률 추첨이 아니라
 * 도감 순서 지급이라 쓸 자리가 없기 때문이다("ERD.md" 5-4). 컬럼 자체는 DB 에
 * 남겨 두었다. 나중에 추첨 방식으로 바꿀 때 마이그레이션이 필요 없고,
 * ddl-auto=validate 는 "엔티티의 컬럼이 테이블에 있는가" 만 보므로 테이블에만
 * 있는 컬럼은 문제가 되지 않는다.
 */
@Entity
@Table(name = "characters")
public class Character {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 지급 순서의 기준이다. 이 값의 오름차순으로 한 마리씩 준다.
	@Column(name = "code", nullable = false)
	private String code;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "rarity")
	private String rarity;

	// 캐릭터 이미지의 S3 키를 담은 JSON 이다. 문자열 그대로 들고 있다가
	// 응답을 만들 때 한 번 해석한다. 서버는 이 안을 들여다볼 일이 없고,
	// 키 구성이 디자인에 따라 바뀔 수 있어 형태를 코드로 고정하지 않는다.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "assets")
	private String assets;

	// 캐릭터는 지우지 않고 이 값으로 도감에서 감춘다.
	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	protected Character() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getRarity() {
		return rarity;
	}

	public String getAssets() {
		return assets;
	}

	public boolean isActive() {
		return isActive;
	}

}
