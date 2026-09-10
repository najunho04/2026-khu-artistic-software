package artistic.software.khu.artistic_software_khu.deviceapi;

import artistic.software.khu.artistic_software_khu.character.CharacterService;
import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import artistic.software.khu.artistic_software_khu.device.Device;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import artistic.software.khu.artistic_software_khu.routine.RoutineDayResponse;
import artistic.software.khu.artistic_software_khu.routine.RoutineService;
import artistic.software.khu.artistic_software_khu.routine.SmallRoutine;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 동기화. "API.md" 8장 POST /device-api/v1/sync.
 *
 * **이 서비스가 아이에게 루틴이 닿는 유일한 경로다.** 앞의 모든 구간이 "앱이
 * 데이터를 넣는" 쪽이었고, 여기서 처음으로 그 데이터가 기기로 나간다.
 *
 * 순서가 중요하다. **push 를 먼저 하고 pull 을 나중에 한다.** 반대로 하면
 * 방금 올린 완료가 같은 응답의 루틴 목록에 빠져, 기기가 "올렸는데 반영이 안
 * 됐네" 하고 다시 올린다.
 *
 * 한 트랜잭션 안에서 처리하므로 완료만 반영되고 루틴은 못 받는 중간 상태가
 * 생기지 않는다.
 */
@Service
public class DeviceSyncService {

	private static final int MINIMUM_BATTERY = 0;

	private static final int MAXIMUM_BATTERY = 100;

	private final DeviceRepository deviceRepository;

	private final RoutineService routineService;

	private final CharacterService characterService;

	private final Clock clock;

	// 한 번에 받아 갈 수 있는 날짜 수. "API.md" 15장 #8 이 아직 미확정이라
	// 설정값으로 뺀다. 문서가 3일을 제안하고 있어 그 값을 기본으로 둔다.
	//
	// 상한이 필요한 이유는 받는 쪽이 임베디드 장치이기 때문이다. 기기가 1년치를
	// 한 번에 요청하면 응답이 기기의 메모리를 넘길 수 있고, 그러면 그 기기는
	// 그 뒤로 아무것도 받지 못한다.
	private final int maximumSyncDateCount;

	public DeviceSyncService(
		DeviceRepository deviceRepository,
		RoutineService routineService,
		CharacterService characterService,
		Clock clock,
		@Value("${yeso.device.max-sync-date-count:3}") int maximumSyncDateCount) {

		this.deviceRepository = deviceRepository;
		this.routineService = routineService;
		this.characterService = characterService;
		this.clock = clock;
		this.maximumSyncDateCount = maximumSyncDateCount;
	}

	@Transactional
	public SyncResponse sync(Long deviceId, SyncRequest request) {
		validate(request);

		Device device = deviceRepository.findByIdAndDeletedAtIsNull(deviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_UNAUTHORIZED));

		Instant now = clock.instant();

		// push — 완료 기록과 기기 상태를 반영한다.
		RoutineService.CompletionResult completionResult = routineService.applyCompletions(
			device.getChildId(), toApplications(request.completions()));

		device.recordSync(request.battery(), request.firmware(), now);

		// 캐릭터는 "이번에 처음 완료된" 개수만큼 자란다("API.md" 12-1).
		// 반영한 기록 수(accepted) 를 쓰면 기기가 같은 요청을 재전송할 때마다
		// 캐릭터가 자라서, 아이의 성장이 네트워크 상태에 좌우된다.
		characterService.grantExperience(
			device.getChildId(), completionResult.newlyCompleted());

		// pull — 요청한 날짜의 루틴을 읽는다. push 뒤에 두어야 방금 올린
		// 완료가 이 응답에 담긴다.
		List<RoutineDayResponse> routines =
			routineService.findRoutinesOn(device.getChildId(), request.dates());

		return new SyncResponse(now, completionResult.accepted(), routines);
	}

	private void validate(SyncRequest request) {
		if (request.battery() != null
			&& (request.battery() < MINIMUM_BATTERY || request.battery() > MAXIMUM_BATTERY)) {

			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		if (request.dates() != null && request.dates().size() > maximumSyncDateCount) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		validateCompletionStatuses(request.completions());
	}

	/**
	 * 완료 기록의 상태 값을 검사한다. "DONE" 과 "PENDING" 만 받는다.
	 *
	 * 모르는 값을 조용히 넘기면 안 된다. 완료 반영은 "DONE 이 아니면 전부
	 * PENDING" 으로 처리하므로, 펌웨어의 오타 하나로 아이가 한 일이 통째로
	 * 지워진다. 그것도 200 응답과 함께 지워져서 기기 쪽에서는 잘못된 것을
	 * 알아챌 방법이 없다.
	 *
	 * 하나라도 이상하면 요청 전체를 거절한다. 이상한 것만 빼고 나머지를
	 * 반영하면 기기와 서버의 상태가 조금씩 어긋난 채로 굳는다.
	 */
	private void validateCompletionStatuses(List<CompletionRecord> completions) {
		if (completions == null) {
			return;
		}

		for (CompletionRecord completion : completions) {
			boolean isKnownStatus = SmallRoutine.STATUS_DONE.equals(completion.status())
				|| SmallRoutine.STATUS_PENDING.equals(completion.status());

			if (!isKnownStatus) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
		}
	}

	/**
	 * 기기 요청 형태를 루틴 도메인이 아는 형태로 옮긴다.
	 *
	 * routine 패키지가 deviceapi 패키지를 알지 않게 하기 위해서다. 루틴 도메인은
	 * 누가 완료를 올렸는지 알 필요가 없다.
	 */
	private List<RoutineService.CompletionApplication> toApplications(
		List<CompletionRecord> completions) {

		if (completions == null) {
			return List.of();
		}

		return completions.stream()
			.map(completion -> new RoutineService.CompletionApplication(
				completion.smallRoutineId(), completion.status(), completion.completedAt()))
			.toList();
	}

}
