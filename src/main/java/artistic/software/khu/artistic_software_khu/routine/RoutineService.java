package artistic.software.khu.artistic_software_khu.routine;

import artistic.software.khu.artistic_software_khu.child.Child;
import artistic.software.khu.artistic_software_khu.child.ChildService;
import artistic.software.khu.artistic_software_khu.common.BusinessException;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빅루틴 · 스몰루틴. "API.md" 9장.
 *
 * 이 서비스가 지키는 규칙은 크게 셋이다.
 *
 * 1. **소유권** — 모든 진입점이 "이 자녀가 내 자녀인가" 를 먼저 묻는다.
 *    경로에 자녀 id 가 없는 것(빅루틴 id, 스몰루틴 id)도 대상에서 자녀를
 *    거슬러 올라가 확인한다. 검사가 없으면 id 를 1, 2, 3 으로 바꿔가며
 *    남의 아이 루틴을 읽고 고칠 수 있다.
 * 2. **전파 범위** — 반복으로 만든 루틴은 사용자 눈에 하나이므로 같은
 *    시리즈 전체에 적용한다. 다만 오늘보다 이전 날짜는 건드리지 않는다.
 * 3. **즉시 생성** — 요청을 받는 순간 만들 행이 전부 정해진다.
 *    조회 시점에 만들어내는 지연 생성은 없다.
 */
@Service
public class RoutineService {

	// "API.md" 9장에서 확정한 DATES 모드의 날짜 개수 상한.
	private static final int MAXIMUM_DATE_COUNT = 12;

	// "ERD.md" 5-2 — 날짜 경계는 KST 기준으로 판단한다. 서버는 UTC 로 돌지만
	// "오늘" 은 사용자가 사는 곳의 오늘이어야 한다. UTC 로 판단하면 한국 시각
	// 오전 9시 이전에 어제 날짜가 "오늘 이후" 로 잘못 걸린다.
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final BigRoutineRepository bigRoutineRepository;

	private final SmallRoutineRepository smallRoutineRepository;

	private final RoutineTemplateService routineTemplateService;

	private final ChildService childService;

	private final Clock clock;

	// RANGE 모드의 기간 길이 상한. "API.md" 15장 #7 이 아직 미확정이라
	// 설정값으로 뺀다. 확정되면 이 기본값만 바꾸면 된다.
	private final int maximumDateRangeLength;

	public RoutineService(
		BigRoutineRepository bigRoutineRepository,
		SmallRoutineRepository smallRoutineRepository,
		RoutineTemplateService routineTemplateService,
		ChildService childService,
		Clock clock,
		@Value("${yeso.routine.max-date-range-length:31}") int maximumDateRangeLength) {

		this.bigRoutineRepository = bigRoutineRepository;
		this.smallRoutineRepository = smallRoutineRepository;
		this.routineTemplateService = routineTemplateService;
		this.childService = childService;
		this.clock = clock;
		this.maximumDateRangeLength = maximumDateRangeLength;
	}

	// ------------------------------------------------------------------
	// 생성
	// ------------------------------------------------------------------

	@Transactional
	public BigRoutineCreateResponse create(
		Long userId, Long childId, BigRoutineCreateRequest request) {

		childService.findOwnedChild(userId, childId);

		// 양식은 "한 번만" 읽어 아래로 넘긴다. 제목과 시각과 할 일이 각자
		// 따로 읽으면 같은 행을 세 번 조회하게 되고, 그 사이에 값이 바뀌면
		// 한 루틴 안에서 서로 다른 양식의 값이 섞일 수 있다.
		RoutineTemplate template = loadTemplateIfGiven(userId, request);

		BigRoutineCreationPlan plan = buildPlan(request, template);
		List<SmallRoutineRequest> smallRoutineRequests = resolveSmallRoutines(request, template);

		for (LocalDate routineDate : plan.routineDates()) {
			BigRoutine bigRoutine =
				bigRoutineRepository.save(BigRoutine.from(childId, plan, routineDate));

			createSmallRoutines(bigRoutine, smallRoutineRequests);
		}

		return BigRoutineCreateResponse.from(plan);
	}

	/**
	 * 반복 모드에 따라 날짜를 펼친다.
	 *
	 * 모드별 규칙 자체는 BigRoutineCreationPlan 이 들고 있다. 여기서 하는 일은
	 * 요청의 문자열을 열거형으로 바꾸고 알맞은 만들기 메서드로 넘기는 것뿐이다.
	 * 규칙과 저장을 갈라 두어야 규칙을 DB 없이 검증할 수 있다.
	 */
	private BigRoutineCreationPlan buildPlan(
		BigRoutineCreateRequest request, RoutineTemplate template) {

		RepeatType repeatType = parseRepeatType(request.repeatType());

		String title = resolveTitle(request, template);
		LocalTime startTime = resolveStartTime(request, template);
		LocalTime endTime = resolveEndTime(request, template);

		if (startTime == null || endTime == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		return switch (repeatType) {
			case RANGE -> BigRoutineCreationPlan.ofRange(
				title, startTime, endTime,
				request.startDate(), request.endDate(), maximumDateRangeLength);

			case WEEKLY -> BigRoutineCreationPlan.ofWeekly(
				title, startTime, endTime,
				request.startDate(), request.endDate(),
				parseRepeatDays(request.repeatDays()), maximumDateRangeLength);

			case DATES -> BigRoutineCreationPlan.ofDates(
				title, startTime, endTime,
				request.repeatDates(), MAXIMUM_DATE_COUNT);
		};
	}

	private void createSmallRoutines(
		BigRoutine bigRoutine, List<SmallRoutineRequest> requests) {

		int order = 1;

		for (SmallRoutineRequest request : requests) {
			// 스몰루틴의 seriesId 는 빅루틴과 별개로 발급한다. "양치하기 미션"
			// 하나의 이행률을 날짜에 걸쳐 모으려면 그 할 일만의 식별자가 필요하다.
			//
			// 다만 같은 빅루틴 시리즈 안에서는 "같은 순서의 할 일" 이 같은 값을
			// 가져야 한다. 그래서 빅루틴 시리즈와 순서를 섞어 만든다. 무작위로
			// 만들면 9월 1일의 양치하기와 9월 2일의 양치하기가 다른 미션이 된다.
			UUID smallRoutineSeriesId = deriveSmallRoutineSeriesId(bigRoutine.getSeriesId(), order);

			smallRoutineRepository.save(SmallRoutine.create(
				bigRoutine.getId(), smallRoutineSeriesId, request.title(), order));

			order++;
		}
	}

	/**
	 * 빅루틴 시리즈와 순서를 섞어 스몰루틴의 시리즈 값을 만든다.
	 *
	 * 같은 입력이면 항상 같은 값이 나오므로, 같은 시리즈의 여러 날짜에서
	 * 같은 순서의 할 일이 자연히 같은 값을 갖는다. 저장해 두고 찾아 쓰지
	 * 않아도 되어 만드는 쪽이 단순해진다.
	 */
	private UUID deriveSmallRoutineSeriesId(UUID bigRoutineSeriesId, int order) {
		return UUID.nameUUIDFromBytes(
			(bigRoutineSeriesId.toString() + ":" + order).getBytes());
	}

	/**
	 * 양식이 지정됐으면 읽어 온다. 없으면 null 이다.
	 *
	 * 여기서 소유권 검사도 함께 일어난다(RoutineTemplateService.findOwnedTemplate).
	 * 검사가 없으면 templateId 를 1, 2, 3 으로 바꿔가며 남이 저장해둔 양식의
	 * 내용을 자기 루틴으로 만들어 읽을 수 있다.
	 */
	private RoutineTemplate loadTemplateIfGiven(Long userId, BigRoutineCreateRequest request) {
		if (request.templateId() == null) {
			return null;
		}

		return routineTemplateService.findOwnedTemplate(userId, request.templateId());
	}

	/**
	 * 값을 정하는 규칙은 네 곳 모두 같다. **요청에 값이 있으면 요청이 이기고,
	 * 없으면 양식 값으로 채운다.**
	 *
	 * 요청을 우선하는 이유는 앱의 흐름 때문이다. 앱은 양식을 불러와 화면을
	 * 채우고, 사용자가 시간이나 제목을 고친 뒤 저장한다. 양식이 이기면
	 * 사용자가 고친 값이 무시되고, 왜 안 바뀌는지 알 수 없게 된다.
	 *
	 * 양식은 "기본값" 이고 요청은 "이번에 실제로 쓸 값" 이다.
	 */
	private String resolveTitle(BigRoutineCreateRequest request, RoutineTemplate template) {
		if (request.title() != null && !request.title().isBlank()) {
			return request.title();
		}

		if (template != null) {
			return template.getTitle();
		}

		// 양식도 없고 제목도 없으면 무엇을 만들지 알 수 없다.
		throw new BusinessException(ErrorCode.INVALID_INPUT);
	}

	private LocalTime resolveStartTime(BigRoutineCreateRequest request, RoutineTemplate template) {
		if (request.startTime() != null) {
			return request.startTime();
		}

		return (template == null) ? null : template.getStartTime();
	}

	private LocalTime resolveEndTime(BigRoutineCreateRequest request, RoutineTemplate template) {
		if (request.endTime() != null) {
			return request.endTime();
		}

		return (template == null) ? null : template.getEndTime();
	}

	private List<SmallRoutineRequest> resolveSmallRoutines(
		BigRoutineCreateRequest request, RoutineTemplate template) {

		if (request.smallRoutines() != null && !request.smallRoutines().isEmpty()) {
			return request.smallRoutines();
		}

		if (template != null) {
			return routineTemplateService.readSmallRoutines(template);
		}

		return List.of();
	}

	// ------------------------------------------------------------------
	// 조회
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<CalendarDayResponse> findCalendar(
		Long userId, Long childId, LocalDate fromDate, LocalDate toDate) {

		childService.findOwnedChild(userId, childId);

		if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_DATE_RANGE);
		}

		List<BigRoutine> bigRoutines = bigRoutineRepository
			.findAllByChildIdAndRoutineDateBetweenAndDeletedAtIsNullOrderByRoutineDateAscIdAsc(
				childId, fromDate, toDate);

		Map<Long, List<SmallRoutine>> smallRoutinesByBigRoutineId =
			loadSmallRoutines(bigRoutines);

		// 날짜별로 묶는다. 순서를 지키려고 LinkedHashMap 을 쓴다.
		Map<LocalDate, List<BigRoutineResponse>> byDate = new LinkedHashMap<>();

		for (BigRoutine bigRoutine : bigRoutines) {
			byDate.computeIfAbsent(bigRoutine.getRoutineDate(), date -> new ArrayList<>())
				.add(BigRoutineResponse.of(
					bigRoutine,
					smallRoutinesByBigRoutineId.getOrDefault(bigRoutine.getId(), List.of())));
		}

		return byDate.entrySet().stream()
			.map(entry -> CalendarDayResponse.of(entry.getKey(), entry.getValue()))
			.toList();
	}

	/**
	 * 지정한 날짜들의 루틴을 읽는다. 기기 동기화(4-1)가 쓴다.
	 *
	 * 소유권 검사를 하지 않는 대신 childId 를 그대로 받는다. 부르는 쪽이
	 * 기기 인증 필터가 이미 확인한 "그 기기가 붙은 자녀" 를 넘기기 때문이다.
	 * 기기는 자기 자녀 말고는 지정할 방법이 없다.
	 *
	 * 루틴을 만들지 않는다. 있는 것만 읽는다. 기기에서는 루틴 생성이
	 * 불가능하고, 빅루틴은 앱이 요청할 때 즉시 만들어진다.
	 */
	@Transactional(readOnly = true)
	public List<RoutineDayResponse> findRoutinesOn(Long childId, List<LocalDate> dates) {
		if (dates == null || dates.isEmpty()) {
			return List.of();
		}

		List<BigRoutine> bigRoutines = bigRoutineRepository
			.findAllByChildIdAndRoutineDateInAndDeletedAtIsNullOrderByRoutineDateAscIdAsc(
				childId, dates);

		Map<Long, List<SmallRoutine>> smallRoutinesByBigRoutineId = loadSmallRoutines(bigRoutines);

		Map<LocalDate, List<BigRoutineResponse>> byDate = new LinkedHashMap<>();

		for (BigRoutine bigRoutine : bigRoutines) {
			byDate.computeIfAbsent(bigRoutine.getRoutineDate(), date -> new ArrayList<>())
				.add(BigRoutineResponse.of(
					bigRoutine,
					smallRoutinesByBigRoutineId.getOrDefault(bigRoutine.getId(), List.of())));
		}

		return byDate.entrySet().stream()
			.map(entry -> new RoutineDayResponse(entry.getKey(), entry.getValue()))
			.toList();
	}

	/**
	 * 완료 기록을 반영한다. 기기 동기화(4-1)가 쓴다.
	 *
	 * **UPDATE 기반이라 멱등성이 자연히 보장된다.** 같은 요청이 다시 와도 같은
	 * 행을 같은 값으로 덮어쓸 뿐이다. 기기는 네트워크가 끊기면 재전송 외에 할 수
	 * 있는 일이 없으므로 이 성질이 없으면 완료가 두 번 쌓인다.
	 *
	 * 없는 할 일은 **건너뛴다.** 기기가 오프라인인 동안 보호자가 지웠을 뿐이고
	 * 흔하게 일어난다. 전체를 실패시키면 기기는 재시도밖에 못 하는데 다시 보내도
	 * 똑같이 실패해, 그 뒤의 완료가 영원히 올라가지 못한다.
	 *
	 * @return 실제로 반영된 개수
	 */
	@Transactional
	public int applyCompletions(Long childId, List<CompletionApplication> completions) {
		if (completions == null || completions.isEmpty()) {
			return 0;
		}

		int accepted = 0;

		for (CompletionApplication completion : completions) {
			if (completion.smallRoutineId() == null) {
				continue;
			}

			SmallRoutine smallRoutine = smallRoutineRepository
				.findByIdAndDeletedAtIsNull(completion.smallRoutineId())
				.orElse(null);

			if (smallRoutine == null) {
				continue;
			}

			// 다른 아이의 할 일이 섞여 들어오면 건너뛴다. 기기가 id 를 지어내
			// 남의 아이 기록을 고치는 것을 막는다.
			BigRoutine bigRoutine = bigRoutineRepository
				.findByIdAndDeletedAtIsNull(smallRoutine.getBigRoutineId())
				.orElse(null);

			if (bigRoutine == null || !bigRoutine.getChildId().equals(childId)) {
				continue;
			}

			smallRoutine.applyCompletion(completion.status(), completion.completedAt());
			accepted++;
		}

		return accepted;
	}

	/**
	 * 완료 기록 하나를 서비스 안에서 다루는 형태.
	 *
	 * 기기 요청 DTO 를 그대로 받지 않는 이유는 routine 패키지가 deviceapi 패키지를
	 * 알게 되기 때문이다. 루틴 도메인은 누가 완료를 올렸는지 알 필요가 없다.
	 */
	public record CompletionApplication(
		Long smallRoutineId, String status, java.time.Instant completedAt) {
	}

	/**
	 * 여러 빅루틴의 할 일을 한 번에 읽어 빅루틴 id 로 묶는다.
	 *
	 * 빅루틴마다 따로 물어보면 한 달치 조회에 수십 번의 질의가 나간다.
	 */
	private Map<Long, List<SmallRoutine>> loadSmallRoutines(List<BigRoutine> bigRoutines) {
		if (bigRoutines.isEmpty()) {
			return Map.of();
		}

		List<Long> bigRoutineIds = bigRoutines.stream().map(BigRoutine::getId).toList();

		return smallRoutineRepository
			.findAllByBigRoutineIdInAndDeletedAtIsNullOrderBySortOrderAscIdAsc(bigRoutineIds)
			.stream()
			.collect(Collectors.groupingBy(SmallRoutine::getBigRoutineId));
	}

	// ------------------------------------------------------------------
	// 수정 · 삭제
	// ------------------------------------------------------------------

	@Transactional
	public BigRoutineResponse update(
		Long userId, Long bigRoutineId, BigRoutineUpdateRequest request, String scope) {

		BigRoutine bigRoutine = findOwnedBigRoutine(userId, bigRoutineId);

		if (isSeriesScope(scope, true)) {
			// 반복으로 만든 루틴은 사용자 눈에 하나다. 월요일만 바뀌고
			// 수 · 금이 그대로면 이상하다.
			for (BigRoutine sibling : findFutureSiblings(bigRoutine)) {
				sibling.update(request.title(), request.startTime(), request.endTime());
			}
		}

		// 대상 자체는 과거여도 고칠 수 있게 둔다. 사용자가 그 날짜를 콕 집어
		// 고치는 중이므로, 그것까지 막으면 오타 하나를 영영 못 고친다.
		bigRoutine.update(request.title(), request.startTime(), request.endTime());

		return BigRoutineResponse.of(bigRoutine, findSmallRoutines(bigRoutineId));
	}

	@Transactional
	public void delete(Long userId, Long bigRoutineId, String scope) {
		BigRoutine bigRoutine = findOwnedBigRoutine(userId, bigRoutineId);

		// 삭제는 기본이 single 이다. 수정과 반대인 것은 의도한 것으로,
		// 수정은 되돌릴 수 있지만 삭제는 어렵기 때문이다.
		if (isSeriesScope(scope, false)) {
			for (BigRoutine sibling : findFutureSiblings(bigRoutine)) {
				sibling.delete(clock.instant());
			}
		}

		bigRoutine.delete(clock.instant());
	}

	// ------------------------------------------------------------------
	// 스몰루틴
	// ------------------------------------------------------------------

	@Transactional
	public SmallRoutineResponse addSmallRoutine(
		Long userId, Long bigRoutineId, SmallRoutineRequest request, String scope) {

		BigRoutine bigRoutine = findOwnedBigRoutine(userId, bigRoutineId);

		if (request.title() == null || request.title().isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		int nextOrder = findSmallRoutines(bigRoutineId).size() + 1;
		UUID seriesId = deriveSmallRoutineSeriesId(bigRoutine.getSeriesId(), nextOrder);

		// 과거 날짜에는 더하지 않는다. 할 일 개수가 늘면 그 날의 이행률
		// 분모가 커져 "이미 지나간 날의 성적" 이 떨어진다. 아이가 아무것도
		// 하지 않았는데 지난주 이행률이 나빠지는 셈이다.
		if (isSeriesScope(scope, true)) {
			for (BigRoutine sibling : findFutureSiblings(bigRoutine)) {
				smallRoutineRepository.save(SmallRoutine.create(
					sibling.getId(), seriesId, request.title(), nextOrder));
			}
		}

		SmallRoutine created = smallRoutineRepository.save(
			SmallRoutine.create(bigRoutineId, seriesId, request.title(), nextOrder));

		return SmallRoutineResponse.from(created);
	}

	@Transactional
	public SmallRoutineResponse updateSmallRoutine(
		Long userId, Long smallRoutineId, SmallRoutineRequest request) {

		SmallRoutine smallRoutine = findOwnedSmallRoutine(userId, smallRoutineId);

		// seriesId 는 건드리지 않는다. 이름이 바뀌어도 "원래 같은 미션" 이라는
		// 사실은 그대로이고, 이름을 고쳤다고 통계가 두 갈래로 갈라지면 안 된다.
		smallRoutine.updateTitle(request.title());

		return SmallRoutineResponse.from(smallRoutine);
	}

	@Transactional
	public void deleteSmallRoutine(Long userId, Long smallRoutineId) {
		findOwnedSmallRoutine(userId, smallRoutineId).delete(clock.instant());
	}

	@Transactional
	public List<SmallRoutineResponse> reorderSmallRoutines(
		Long userId, Long bigRoutineId, List<Long> orderedIds) {

		findOwnedBigRoutine(userId, bigRoutineId);

		List<SmallRoutine> smallRoutines = findSmallRoutines(bigRoutineId);

		// 요청 배열과 DB 목록을 먼저 대조한다. 하나라도 빠지거나 남으면
		// 정렬을 아예 시작하지 않는다. 반쯤 바뀐 상태로 끝나면 순서가
		// 뒤죽박죽이 되고 되돌릴 방법이 없다.
		Set<Long> requestedIds = Set.copyOf(orderedIds);
		Set<Long> actualIds = smallRoutines.stream()
			.map(SmallRoutine::getId)
			.collect(Collectors.toSet());

		if (orderedIds.size() != requestedIds.size() || !requestedIds.equals(actualIds)) {
			throw new BusinessException(ErrorCode.ROUTINE_ORDER_MISMATCH);
		}

		Map<Long, SmallRoutine> byId = smallRoutines.stream()
			.collect(Collectors.toMap(SmallRoutine::getId, smallRoutine -> smallRoutine));

		int order = 1;

		for (Long smallRoutineId : orderedIds) {
			byId.get(smallRoutineId).changeSortOrder(order);
			order++;
		}

		return findSmallRoutines(bigRoutineId).stream()
			.map(SmallRoutineResponse::from)
			.toList();
	}

	// ------------------------------------------------------------------
	// 소유권 · 도우미
	// ------------------------------------------------------------------

	/**
	 * 빅루틴에서 자녀를 거슬러 올라가 소유권을 확인한다.
	 *
	 * 경로에 자녀 id 가 없어도 검사한다. 이 검사가 없으면 bigRoutineId 를
	 * 1, 2, 3 으로 바꿔가며 남의 아이 루틴을 전부 고칠 수 있다.
	 */
	private BigRoutine findOwnedBigRoutine(Long userId, Long bigRoutineId) {
		BigRoutine bigRoutine = bigRoutineRepository.findByIdAndDeletedAtIsNull(bigRoutineId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BIG_ROUTINE_NOT_FOUND));

		Child child = childService.findOwnedChild(userId, bigRoutine.getChildId());

		if (!child.isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.CHILD_FORBIDDEN);
		}

		return bigRoutine;
	}

	private SmallRoutine findOwnedSmallRoutine(Long userId, Long smallRoutineId) {
		SmallRoutine smallRoutine = smallRoutineRepository
			.findByIdAndDeletedAtIsNull(smallRoutineId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SMALL_ROUTINE_NOT_FOUND));

		findOwnedBigRoutine(userId, smallRoutine.getBigRoutineId());

		return smallRoutine;
	}

	/**
	 * 같은 시리즈에서 "오늘 이후" 날짜의 다른 행들. 대상 자신은 빠진다.
	 */
	private List<BigRoutine> findFutureSiblings(BigRoutine bigRoutine) {
		if (bigRoutine.getSeriesId() == null) {
			return List.of();
		}

		return bigRoutineRepository
			.findAllBySeriesIdAndRoutineDateGreaterThanEqualAndDeletedAtIsNull(
				bigRoutine.getSeriesId(), today())
			.stream()
			.filter(sibling -> !sibling.getId().equals(bigRoutine.getId()))
			.sorted(Comparator.comparing(BigRoutine::getRoutineDate))
			.toList();
	}

	private List<SmallRoutine> findSmallRoutines(Long bigRoutineId) {
		return smallRoutineRepository
			.findAllByBigRoutineIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(bigRoutineId);
	}

	/**
	 * KST 기준 오늘 날짜. 서버는 UTC 로 돌지만 "오늘" 은 사용자가 사는 곳의 오늘이다.
	 */
	private LocalDate today() {
		return LocalDate.now(clock.withZone(KOREA_ZONE));
	}

	/**
	 * scope 값을 읽는다. 값이 없으면 엔드포인트별 기본값을 쓴다.
	 *
	 * 수정은 series 가 기본이고 삭제는 single 이 기본이다. 반대인 것은
	 * 의도한 것으로, 수정은 되돌릴 수 있지만 삭제는 어렵기 때문이다.
	 */
	private boolean isSeriesScope(String scope, boolean defaultIsSeries) {
		if (scope == null || scope.isBlank()) {
			return defaultIsSeries;
		}

		return "series".equalsIgnoreCase(scope);
	}

	private RepeatType parseRepeatType(String value) {
		if (value == null) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}

		try {
			return RepeatType.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}
	}

	private Set<DayOfWeek> parseRepeatDays(List<String> values) {
		if (values == null) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}

		try {
			return values.stream()
				.map(RoutineService::toDayOfWeek)
				.collect(Collectors.toSet());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.ROUTINE_INVALID_REPEAT_RULE);
		}
	}

	/**
	 * "MON" 같은 세 글자 표기를 요일로 바꾼다. "API.md" 9장이 정한 형식이다.
	 */
	private static DayOfWeek toDayOfWeek(String value) {
		return switch (value.toUpperCase()) {
			case "MON" -> DayOfWeek.MONDAY;
			case "TUE" -> DayOfWeek.TUESDAY;
			case "WED" -> DayOfWeek.WEDNESDAY;
			case "THU" -> DayOfWeek.THURSDAY;
			case "FRI" -> DayOfWeek.FRIDAY;
			case "SAT" -> DayOfWeek.SATURDAY;
			case "SUN" -> DayOfWeek.SUNDAY;
			default -> throw new IllegalArgumentException("알 수 없는 요일: " + value);
		};
	}

}
