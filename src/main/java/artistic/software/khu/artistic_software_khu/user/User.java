package artistic.software.khu.artistic_software_khu.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 보호자 계정. "ERD.md" 1장 USERS 를 그대로 옮긴다.
 *
 * "provider" 와 "provider_user_id" 컬럼은 매핑하지 않는다. 소셜 로그인 시절의
 * 컬럼이고 지금은 아무 값도 들어가지 않기 때문이다. 컬럼 자체는 DB 에 남겨
 * 두었지만(V2 에서 nullable 로 완화) 코드가 읽을 일이 없다.
 * ddl-auto=validate 는 "엔티티의 컬럼이 테이블에 있는가" 만 보므로,
 * 테이블에만 있고 엔티티에 없는 컬럼은 문제가 되지 않는다.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false)
	private String email;

	// 비밀번호를 되돌릴 수 없는 형태로 바꿔 저장한 값. 평문은 어디에도 남기지 않는다.
	// bcrypt 를 쓰므로 무작위 값(salt) 이 결과 문자열 안에 함께 들어 있어
	// 별도의 salt 컬럼이 필요 없다.
	@Column(name = "password_hash")
	private String passwordHash;

	// 로그인에 성공하면 서버가 발급하는 값. 앱이 이후 요청마다 헤더에 담아 보낸다.
	// 로그아웃하면 비워지고, 비어 있으면 그 계정은 로그인 상태가 아니다.
	@Column(name = "access_uuid")
	private UUID accessUuid;

	// 가입 직후에는 아직 성명을 받기 전이라 비어 있다. 온보딩 1차에서 채운다.
	// 이 값이 비어 있다는 것이 앱에게 "온보딩을 이어서 하라" 는 신호가 된다.
	@Column(name = "name")
	private String name;

	// 값을 DB 가 채운다(V1 의 "default now()"). insertable = false 로 두어
	// 하이버네이트가 INSERT 에 이 컬럼을 넣지 않게 한다.
	//
	// "@Generated(event = INSERT)" 가 반드시 필요하다. 이것이 없으면 저장한
	// 뒤에도 객체의 값이 null 로 남는다. 하이버네이트가 INSERT 만 하고 결과를
	// 다시 읽지 않기 때문이다. 같은 트랜잭션 안에서 방금 만든 유저를 조회하면
	// createdAt 이 비어 나가는데, DB 에는 값이 멀쩡히 들어 있어서 원인을
	// 찾기가 헷갈린다.
	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	// soft delete. 값이 차 있으면 탈퇴한 계정이다.
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected User() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private User(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
	}

	/**
	 * 가입한 계정을 만든다. 비밀번호는 "이미 해시된 값" 을 받는다.
	 *
	 * 평문을 받아 이 안에서 해시하지 않는 이유는, 그렇게 하면 엔티티가
	 * 해시 방식을 알아야 하기 때문이다. 해시 방식은 언제든 바뀔 수 있는 선택이고
	 * 엔티티는 그것을 모르는 편이 낫다.
	 */
	public static User signUp(String email, String passwordHash) {
		return new User(email, passwordHash);
	}

	/**
	 * 새 접속 값을 발급해 기존 값을 덮어쓴다.
	 *
	 * 덮어쓰기 때문에 이전 값이 그 자리에서 무효가 된다. 폐기 목록을 따로
	 * 관리할 필요가 없다. 대신 한 계정은 항상 한 기기에서만 로그인 상태가 된다.
	 */
	public UUID issueAccessUuid() {
		this.accessUuid = UUID.randomUUID();
		return this.accessUuid;
	}

	/**
	 * 접속 값을 비운다. 만료가 없는 구조라 이것이 값을 무효로 만드는 유일한 수단이다.
	 */
	public void clearAccessUuid() {
		this.accessUuid = null;
	}

	/**
	 * 보호자 성명을 채운다. 온보딩 1차에서 처음 불리고, 이후 수정에도 쓰인다.
	 */
	public void changeName(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UUID getAccessUuid() {
		return accessUuid;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
