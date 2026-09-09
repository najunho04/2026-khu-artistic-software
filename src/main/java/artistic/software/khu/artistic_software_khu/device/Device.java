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
 * 기기. "ERD.md" 1장 DEVICES.
 *
 * 페어링 관련 컬럼을 Phase 2-2 에서, 배터리 · 펌웨어 · 마지막 동기화 시각을
 * Phase 4-1(동기화)에서 더했다. 필요해진 시점에 하나씩 붙였다는 뜻이다.
 * 쓰지 않는 필드를 미리 만들어 두면 "이 값이 채워지고 있나" 를 매번 확인해야 한다.
 *
 * ddl-auto=validate 는 엔티티에 없는 테이블 컬럼을 문제 삼지 않으므로
 * 이렇게 일부만 매핑해도 기동에 지장이 없다.
 *
 * **상태 흐름은 PENDING -> ACTIVE 하나뿐이다.** 앱이 페어링을 요청하면 코드만
 * 들어 있는 PENDING 행이 생기고, 기기가 그 코드로 claim 하면 ACTIVE 가 된다.
 * 해제는 상태를 바꾸지 않고 deleted_at 을 채운다. 상태와 삭제를 한 컬럼에
 * 섞으면 "해제된 PENDING" 같은 것을 표현할 수 없다.
 */
@Entity
@Table(name = "devices")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "child_id", nullable = false)
	private Long childId;

	// 기기가 claim 할 때 스스로 만들어 전달하는 값. 페어링 전에는 비어 있다.
	@Column(name = "device_uid")
	private String deviceUid;

	// 앱이 발급받아 핫스팟으로 기기에 넘기는 일회용 코드. 숫자 10자리다.
	// claim 이 끝나면 NULL 로 비운다. 비우지 않으면 같은 코드로 다른 기기가
	// 또 붙을 수 있다.
	@Column(name = "pairing_code")
	private String pairingCode;

	@Column(name = "pairing_code_expires_at")
	private Instant pairingCodeExpiresAt;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "nickname")
	private String nickname;

	@Column(name = "paired_at")
	private Instant pairedAt;

	// 아래 셋은 기기가 sync 할 때마다 갱신된다. 앱의 기기 목록 화면이
	// "배터리가 얼마나 남았나", "마지막으로 언제 연결됐나" 를 보여주는 근거다.
	//
	// 페어링만 하고 아직 한 번도 sync 하지 않은 기기는 셋 다 비어 있다.
	// 억지로 0 이나 현재 시각으로 채우지 않는다. "배터리 0%" 와 "아직 모름" 은
	// 완전히 다른 뜻이고, 앱이 그 둘을 구분해 보여줘야 하기 때문이다.
	@Column(name = "battery_level")
	private Integer batteryLevel;

	@Column(name = "firmware_version")
	private String firmwareVersion;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	// 페어링이 완료되는 순간 서버가 발급해 기기에 내려주는 값.
	// 기기는 이후 요청마다 이 값을 헤더에 담는다.
	// 페어링 전(PENDING)에는 아직 발급되지 않아 비어 있다.
	@Column(name = "device_access_uuid")
	private UUID deviceAccessUuid;

	// soft delete. 값이 차 있으면 연결이 해제된 기기다.
	@Column(name = "deleted_at")
	private Instant deletedAt;

	// "ERD.md" 의 status 값.
	public static final String STATUS_PENDING = "PENDING";

	public static final String STATUS_ACTIVE = "ACTIVE";

	protected Device() {
		// JPA 가 객체를 만들 때 쓰는 생성자다. 직접 부르지 않는다.
	}

	private Device(Long childId, String pairingCode, Instant expiresAt, String nickname) {
		this.childId = childId;
		this.pairingCode = pairingCode;
		this.pairingCodeExpiresAt = expiresAt;
		this.nickname = nickname;
		this.status = STATUS_PENDING;
		// device_uid 는 아직 없다. 기기가 claim 할 때 가져온다.
		// V1 에서 not null 이었다면 여기서 막혔겠지만 nullable 이라 괜찮다.
	}

	/**
	 * 페어링을 시작한다. 코드만 들어 있는 PENDING 행을 만든다.
	 *
	 * 이 시점에는 어떤 기기가 붙을지 모른다. 코드가 "이 기기는 누구 것인가" 를
	 * 잇는 유일한 끈이고, 기기가 claim 으로 그 코드를 되돌려주면 그때 이어진다.
	 */
	public static Device startPairing(
		Long childId, String pairingCode, Instant expiresAt, String nickname) {

		return new Device(childId, pairingCode, expiresAt, nickname);
	}

	/**
	 * 충돌로 저장에 실패했을 때 코드만 새로 받는다.
	 *
	 * 100억 조합이라 드물지만 0 은 아니다. 행 전체를 다시 만들지 않는 이유는
	 * 자녀와 별칭은 그대로여야 하기 때문이다.
	 */
	public void regeneratePairingCode(String pairingCode) {
		this.pairingCode = pairingCode;
	}

	/**
	 * 기기가 claim 했다. 상태를 ACTIVE 로 바꾸고 신분증을 발급한다.
	 *
	 * pairing_code 를 비우는 것이 중요하다. 일회용이라 비우지 않으면 같은
	 * 코드로 다른 기기가 또 붙을 수 있다. 비우는 순간 조회에서도 사라지므로
	 * "이미 사용됨" 이 자연히 "찾을 수 없음" 이 된다.
	 */
	public UUID claim(String deviceUid, Instant claimedAt) {
		this.deviceUid = deviceUid;
		this.status = STATUS_ACTIVE;
		this.pairedAt = claimedAt;
		this.pairingCode = null;
		this.pairingCodeExpiresAt = null;
		this.deviceAccessUuid = UUID.randomUUID();

		return this.deviceAccessUuid;
	}

	/**
	 * 코드가 만료됐는지 본다. 만료 정리 배치가 없어도 되는 이유가 이것이다.
	 *
	 * 배치는 오래된 행을 치우는 청소일 뿐이고, 만료 판정 자체는 claim 시점에
	 * 여기서 한다. 배치가 안 돌아도 만료된 코드로는 붙을 수 없다.
	 */
	public boolean isPairingCodeExpired(Instant now) {
		return pairingCodeExpiresAt != null && now.isAfter(pairingCodeExpiresAt);
	}

	/**
	 * 동기화 결과를 기록한다. sync 호출마다 불린다.
	 *
	 * 펌웨어는 값이 왔을 때만 바꾼다. 기기가 보내지 않았다고 해서 이전에 알던
	 * 버전을 지울 이유가 없다.
	 */
	public void recordSync(Integer batteryLevel, String firmwareVersion, Instant syncedAt) {
		if (batteryLevel != null) {
			this.batteryLevel = batteryLevel;
		}

		if (firmwareVersion != null && !firmwareVersion.isBlank()) {
			this.firmwareVersion = firmwareVersion;
		}

		this.lastSyncedAt = syncedAt;
	}

	public void changeNickname(String nickname) {
		if (nickname != null) {
			this.nickname = nickname;
		}
	}

	public void release(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getChildId() {
		return childId;
	}

	public String getDeviceUid() {
		return deviceUid;
	}

	public String getPairingCode() {
		return pairingCode;
	}

	public Instant getPairingCodeExpiresAt() {
		return pairingCodeExpiresAt;
	}

	public String getStatus() {
		return status;
	}

	public String getNickname() {
		return nickname;
	}

	public Instant getPairedAt() {
		return pairedAt;
	}

	public Integer getBatteryLevel() {
		return batteryLevel;
	}

	public String getFirmwareVersion() {
		return firmwareVersion;
	}

	public Instant getLastSyncedAt() {
		return lastSyncedAt;
	}

	public UUID getDeviceAccessUuid() {
		return deviceAccessUuid;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
