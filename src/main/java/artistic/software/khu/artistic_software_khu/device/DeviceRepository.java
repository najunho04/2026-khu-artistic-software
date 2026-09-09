package artistic.software.khu.artistic_software_khu.device;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
