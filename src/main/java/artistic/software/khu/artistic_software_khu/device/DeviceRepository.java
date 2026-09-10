package artistic.software.khu.artistic_software_khu.device;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 기기 조회. 인증에 쓰이므로 "deletedAt is null" 조건이 반드시 붙는다.
 *
 * 조건이 빠지면 연결을 해제한 기기가 계속 자녀의 루틴을 받아 갈 수 있다.
 * 해제는 물리 삭제가 아니라 deleted_at 을 채우는 방식이라 행 자체는 남아 있다.
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {

	Optional<Device> findByDeviceAccessUuidAndDeletedAtIsNull(UUID deviceAccessUuid);

	Optional<Device> findByIdAndDeletedAtIsNull(Long id);

	List<Device> findAllByChildIdAndDeletedAtIsNullOrderByIdAsc(Long childId);

	int countByChildIdAndDeletedAtIsNull(Long childId);

	/**
	 * 페어링 코드로 아직 붙지 않은 기기를 찾는다.
	 *
	 * claim 이 끝나면 코드를 NULL 로 비우므로, 이미 쓴 코드는 자연히 조회되지
	 * 않는다. "이미 사용됨" 을 따로 표시하지 않아도 되는 이유다.
	 */
	Optional<Device> findByPairingCodeAndDeletedAtIsNull(String pairingCode);

	/**
	 * 페어링 코드로 찾되 그 행을 "잠그고" 읽는다. claim 이 쓴다.
	 *
	 * 잠금이 없으면 같은 코드를 들고 두 기기가 동시에 들어왔을 때 둘 다 행을
	 * 읽어 각자 신분증을 발급받는다. 저장은 나중 것만 남으므로, 먼저 200 을
	 * 받은 기기는 서버에 없는 신분증을 들고 다니게 된다. 그 기기는 이후 모든
	 * 요청에서 인증에 실패하는데 자기가 성공했다고 믿고 있어 재페어링
	 * 안내조차 뜨지 않는다.
	 *
	 * 잠금을 걸면 뒤에 온 요청은 앞의 것이 끝날 때까지 기다렸다가 조건을 다시
	 * 본다. 그때는 코드가 이미 비워져 있어 아무것도 찾지 못하고, 문서가 정한
	 * "유효하지 않은 페어링 코드" 로 응답한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select device from Device device"
		+ " where device.pairingCode = :pairingCode and device.deletedAt is null")
	Optional<Device> findByPairingCodeAndDeletedAtIsNullForUpdate(
		@Param("pairingCode") String pairingCode);

	/**
	 * 한 자녀에게 붙은 살아 있는 기기를 모두 해제한다. 자녀 삭제와 함께 쓴다.
	 *
	 * 한 행씩 읽어 고치지 않고 UPDATE 한 번으로 처리하는 이유는, 기기가 최대
	 * 10대라 적기는 해도 이 작업에 필요한 것이 "deleted_at 을 채우는 것" 하나뿐이기
	 * 때문이다. 엔티티를 전부 읽어 올 이유가 없다.
	 *
	 * "deleted_at is null" 조건을 거는 이유는 이미 해제된 기기의 해제 시각을
	 * 덮어쓰지 않기 위해서다. 그 시각은 그 기기가 언제 떨어져 나갔는지를
	 * 담고 있는 기록이다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Device device set device.deletedAt = :deletedAt"
		+ " where device.childId = :childId and device.deletedAt is null")
	int releaseAllByChildId(@Param("childId") Long childId, @Param("deletedAt") Instant deletedAt);

}
