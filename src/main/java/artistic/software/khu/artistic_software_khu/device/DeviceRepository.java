package artistic.software.khu.artistic_software_khu.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 기기 조회. 인증에 쓰이므로 "deletedAt is null" 조건이 반드시 붙는다.
 *
 * 조건이 빠지면 연결을 해제한 기기가 계속 자녀의 루틴을 받아 갈 수 있다.
 * 해제는 물리 삭제가 아니라 deleted_at 을 채우는 방식이라 행 자체는 남아 있다.
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {

	Optional<Device> findByDeviceAccessUuidAndDeletedAtIsNull(UUID deviceAccessUuid);

}
