package artistic.software.khu.artistic_software_khu.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 기기. "ERD.md" 1장 DEVICES 중 "인증에 필요한 만큼만" 매핑한다.
 *
 * 배터리 · 펌웨어 · 페어링 코드 같은 나머지 컬럼을 지금 매핑하지 않는 이유는
 * 그것들을 읽고 쓰는 기능이 Phase 2(페어링)와 Phase 4(동기화)에 있기 때문이다.
 * 쓰지 않는 필드를 미리 만들어 두면 "이 값이 채워지고 있나" 를 매번 확인해야 한다.
 * 해당 Phase 에서 필요한 컬럼을 그때 더한다.
 *
 * ddl-auto=validate 는 엔티티에 없는 테이블 컬럼을 문제 삼지 않으므로
 * 이렇게 일부만 매핑해도 기동에 지장이 없다.
 */
@Entity
@Table(name = "devices")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "child_id", nullable = false)
	private Long childId;

	// 페어링이 완료되는 순간 서버가 발급해 기기에 내려주는 값.
	// 기기는 이후 요청마다 이 값을 헤더에 담는다.
	// 페어링 전(PENDING)에는 아직 발급되지 않아 비어 있다.
	@Column(name = "device_access_uuid")
	private UUID deviceAccessUuid;

	// soft delete. 값이 차 있으면 연결이 해제된 기기다.
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Device() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	public Long getId() {
		return id;
	}

	public Long getChildId() {
		return childId;
	}

	public UUID getDeviceAccessUuid() {
		return deviceAccessUuid;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
