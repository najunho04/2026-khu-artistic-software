package artistic.software.khu.artistic_software_khu.device;

import artistic.software.khu.artistic_software_khu.child.ChildService;
import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페어링과 기기 관리. "API.md" 7장 · 8장.
 *
 * 이 서비스의 특징은 **앱과 기기가 서로 다른 인증으로 같은 행 하나를 주고받는다**는
 * 점이다. 앱이 PENDING 행을 만들어 코드를 받아 가고(핫스팟으로 기기에 전달),
 * 기기가 그 코드로 같은 행을 찾아 ACTIVE 로 바꾼다.
 *
 * 그래서 claim 만 소유권 검사를 하지 않는다. 그 시점의 기기는 아직 아무
 * 신분증도 없고, 코드를 알고 있다는 것 자체가 유일한 자격이다.
 */
@Service
public class DeviceService {

	// "API.md" 3-4 가 확정한 자녀당 기기 상한.
	private static final int MAXIMUM_DEVICE_COUNT = 10;

	// 코드가 살아 있는 시간. "API.md" 15장에서 10분으로 확정했다.
	// 앱의 폴링 타임아웃과 같은 값이라 앱이 포기하는 시점과 코드가 죽는 시점이
	// 일치한다. 어긋나면 "앱은 포기했는데 코드는 살아 있는" 구간이 생긴다.
	private static final Duration PAIRING_CODE_LIFETIME = Duration.ofMinutes(10);

	// 코드가 겹쳤을 때 다시 시도할 횟수. 100억 조합이라 한 번이면 거의 끝나지만,
	// 0 이면 아주 드문 충돌에 사용자가 그냥 실패를 본다.
	private static final int MAXIMUM_CODE_ATTEMPTS = 5;

	private final DeviceRepository deviceRepository;

	private final ChildService childService;

	private final PairingCodeGenerator pairingCodeGenerator;

	private final Clock clock;

	private final String defaultNickname;

	public DeviceService(
		DeviceRepository deviceRepository,
		ChildService childService,
		PairingCodeGenerator pairingCodeGenerator,
		Clock clock,
		@Value("${yeso.device.default-nickname:예소}") String defaultNickname) {

		this.deviceRepository = deviceRepository;
		this.childService = childService;
		this.pairingCodeGenerator = pairingCodeGenerator;
		this.clock = clock;
		this.defaultNickname = defaultNickname;
	}

	// ------------------------------------------------------------------
	// 앱 — 페어링 시작
	// ------------------------------------------------------------------

	@Transactional
	public PairingResponse startPairing(Long userId, PairingRequest request) {
		if (request.childId() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		// 자녀 행을 잠근 채로 센다. 잠그지 않으면 두 요청이 같은 숫자를 보고
		// 둘 다 통과해 자녀당 상한을 넘긴다.
		childService.findOwnedChildForUpdate(userId, request.childId());

		// 해제된 기기는 세지 않는다. 세지 않아야 해제한 뒤 자리가 난다.
		if (deviceRepository.countByChildIdAndDeletedAtIsNull(request.childId())
			>= MAXIMUM_DEVICE_COUNT) {

			throw new BusinessException(ErrorCode.DEVICE_LIMIT_EXCEEDED);
		}

		Instant expiresAt = clock.instant().plus(PAIRING_CODE_LIFETIME);
		String nickname = (request.nickname() == null || request.nickname().isBlank())
			? defaultNickname
			: request.nickname();

		Device device = Device.startPairing(
			request.childId(), pairingCodeGenerator.generate(), expiresAt, nickname);

		return PairingResponse.from(saveWithUniqueCode(device));
	}

	/**
	 * 코드가 겹치면 새로 뽑아 다시 저장한다.
	 *
	 * 미리 "이 코드가 있나" 확인하지 않는 이유는, 확인과 저장 사이에 다른 요청이
	 * 끼어드는 순간이 실제로 존재하기 때문이다. 마지막 방어선은
	 * "unique(pairing_code) where status = 'PENDING'" 제약이고, 그것이 거절하면
	 * 새 코드로 다시 시도한다.
	 *
	 * saveAndFlush 를 쓰는 이유는 제약 위반을 "여기서" 잡기 위해서다. 그냥 두면
	 * 트랜잭션이 끝나는 시점에 터지는데, 그때는 이미 재시도할 자리를 지나쳤다.
	 */
	private Device saveWithUniqueCode(Device device) {
		for (int attempt = 0; attempt < MAXIMUM_CODE_ATTEMPTS; attempt++) {
			try {
				return deviceRepository.saveAndFlush(device);
			} catch (DataIntegrityViolationException exception) {
				device.regeneratePairingCode(pairingCodeGenerator.generate());
			}
		}

		// 100억 조합에서 다섯 번 연속 겹치는 것은 사실상 일어나지 않는다.
		// 여기 도달했다면 난수 생성이 고장났다는 뜻이므로 500 이 맞다.
		throw new IllegalStateException("페어링 코드를 만들지 못했습니다");
	}

	// ------------------------------------------------------------------
	// 기기 — claim (인증 없음)
	// ------------------------------------------------------------------

	@Transactional
	public ClaimResponse claim(ClaimRequest request) {
		if (request.pairingCode() == null || request.deviceUid() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		// claim 이 끝난 코드는 NULL 로 비워지므로, 이미 쓴 코드는 여기서 자연히
		// 걸러진다. "이미 사용됨" 을 따로 표시하지 않아도 되는 이유다.
		// 행을 잠그고 읽는다. 같은 코드로 두 기기가 동시에 들어오면 뒤의 것은
		// 앞의 것이 끝날 때까지 기다렸다가 조건을 다시 보는데, 그때는 코드가
		// 이미 비워져 있어 아무것도 찾지 못한다. 일회용이 실제로 지켜지는 곳이다.
		Device device = deviceRepository
			.findByPairingCodeAndDeletedAtIsNullForUpdate(request.pairingCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_CODE_NOT_FOUND));

		Instant now = clock.instant();

		// 만료 판정을 여기서 한다. 그래서 정리 배치가 없어도 동작한다.
		// 배치는 오래된 행을 치우는 청소일 뿐이다.
		if (device.isPairingCodeExpired(now)) {
			throw new BusinessException(ErrorCode.PAIRING_CODE_EXPIRED);
		}

		UUID deviceAccessUuid = device.claim(request.deviceUid(), now);

		try {
			// unique(device_uid) where deleted_at is null 위반을 여기서 잡는다.
			// 같은 기기가 이미 다른 계정에 붙어 있는 경우다.
			deviceRepository.saveAndFlush(device);
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.DEVICE_UID_ALREADY_PAIRED);
		}

		return new ClaimResponse(device.getId(), deviceAccessUuid, now);
	}

	// ------------------------------------------------------------------
	// 앱 — 조회 · 수정 · 해제
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DeviceResponse> findByChild(Long userId, Long childId) {
		childService.findOwnedChild(userId, childId);

		return deviceRepository.findAllByChildIdAndDeletedAtIsNullOrderByIdAsc(childId).stream()
			.map(DeviceResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public DeviceDetailResponse findOne(Long userId, Long deviceId) {
		return DeviceDetailResponse.from(findOwnedDevice(userId, deviceId));
	}

	@Transactional
	public DeviceDetailResponse update(Long userId, Long deviceId, DeviceUpdateRequest request) {
		// "API.md" 7장이 nickname 을 필수로 두었다. 빈 요청을 200 으로 돌려주면
		// 앱은 바뀌었다고 믿는데 실제로는 아무것도 바뀌지 않아, 화면과 서버가
		// 어긋난 채로 남는다.
		if (request.nickname() == null || request.nickname().isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		Device device = findOwnedDevice(userId, deviceId);

		device.changeNickname(request.nickname());

		return DeviceDetailResponse.from(device);
	}

	@Transactional
	public void release(Long userId, Long deviceId) {
		// soft delete 다. 해제하는 순간 device_access_uuid 로 하는 조회에서
		// 빠지므로 그 기기는 더 이상 인증에 성공하지 못한다. 값을 지우지
		// 않아도 되는 이유이고, 남겨두면 "언제 무엇이 붙어 있었나" 가 남는다.
		findOwnedDevice(userId, deviceId).release(clock.instant());
	}

	/**
	 * 기기에서 자녀를 거슬러 올라가 소유권을 확인한다.
	 *
	 * 경로에 자녀 id 가 없어도 검사한다. 이 검사가 없으면 deviceId 를 1, 2, 3 으로
	 * 바꿔가며 남의 아이 기기를 해제하거나 별칭을 바꿀 수 있다.
	 */
	private Device findOwnedDevice(Long userId, Long deviceId) {
		Device device = deviceRepository.findByIdAndDeletedAtIsNull(deviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

		childService.findOwnedChild(userId, device.getChildId());

		return device;
	}

}
