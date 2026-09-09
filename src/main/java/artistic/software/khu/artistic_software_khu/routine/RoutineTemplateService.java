package artistic.software.khu.artistic_software_khu.routine;

import artistic.software.khu.artistic_software_khu.child.ChildService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 저장해둔 루틴 양식. "API.md" 10장.
 *
 * 지연 생성이 없어지면서 가장 단순한 구간이 되었다. 값을 저장하고 꺼내 쓰는
 * 것이 전부이며, 양식을 고쳐도 이미 만들어진 빅루틴은 바뀌지 않는다.
 * 꺼내 쓰는 순간 값이 복사되고 둘의 관계는 거기서 끝나기 때문이다.
 */
@Service
public class RoutineTemplateService {

	private final RoutineTemplateRepository routineTemplateRepository;

	private final ChildService childService;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	public RoutineTemplateService(
		RoutineTemplateRepository routineTemplateRepository,
		ChildService childService,
		ObjectMapper objectMapper,
		Clock clock) {

		this.routineTemplateRepository = routineTemplateRepository;
		this.childService = childService;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public RoutineTemplateResponse save(
		Long userId, Long childId, RoutineTemplateRequest request) {

		childService.findOwnedChild(userId, childId);

		RoutineTemplate template = RoutineTemplate.save(
			childId, request.title(), request.startTime(), request.endTime(),
			writeSmallRoutines(request.smallRoutines()));

		return toResponse(routineTemplateRepository.save(template));
	}

	@Transactional(readOnly = true)
	public List<RoutineTemplateResponse> findAll(Long userId, Long childId) {
		childService.findOwnedChild(userId, childId);

		return routineTemplateRepository.findAllByChildIdAndDeletedAtIsNullOrderByIdAsc(childId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public RoutineTemplateResponse update(
		Long userId, Long templateId, RoutineTemplateRequest request) {

		RoutineTemplate template = findOwnedTemplate(userId, templateId);

		template.update(
			request.title(), request.startTime(), request.endTime(),
			request.smallRoutines() == null ? null : writeSmallRoutines(request.smallRoutines()));

		return toResponse(template);
	}

	@Transactional
	public void delete(Long userId, Long templateId) {
		findOwnedTemplate(userId, templateId).delete(clock.instant());
	}

	/**
	 * 내 양식 하나를 찾는다. 빅루틴을 만들 때 RoutineService 도 이것을 쓴다.
	 *
	 * 조회와 소유권 검사를 한 곳에 두어야 어느 경로로 들어오든 같게 동작한다.
	 * 양식을 꺼내는 것도 검사를 거쳐야 한다. 검사가 없으면 templateId 를
	 * 1, 2, 3 으로 바꿔가며 남이 저장해둔 양식의 내용을 자기 루틴으로 만들어
	 * 읽을 수 있다.
	 */
	RoutineTemplate findOwnedTemplate(Long userId, Long templateId) {
		RoutineTemplate template = routineTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
			.orElseThrow(() -> new artistic.software.khu.artistic_software_khu.common
				.BusinessException(
				artistic.software.khu.artistic_software_khu.common.ErrorCode
					.ROUTINE_TEMPLATE_NOT_FOUND));

		// 경로에 자녀 id 가 없어도 양식에서 자녀를 거슬러 올라가 확인한다.
		childService.findOwnedChild(userId, template.getChildId());

		return template;
	}

	/**
	 * 양식에 저장된 할 일 목록을 읽는다. 빅루틴을 만들 때 이 값을 복사한다.
	 *
	 * 읽는 방법을 여기 한 곳에만 두는 이유는, 같은 jsonb 문자열을 두 곳에서
	 * 각자 해석하면 한쪽이 형식을 바꿨을 때 다른 쪽이 조용히 깨지기 때문이다.
	 */
	List<SmallRoutineRequest> readSmallRoutines(RoutineTemplate template) {
		if (template.getSmallRoutines() == null) {
			return List.of();
		}

		return objectMapper.readValue(
			template.getSmallRoutines(), new TypeReference<List<SmallRoutineRequest>>() {});
	}

	/**
	 * 할 일 목록을 jsonb 컬럼에 넣을 문자열로 바꾼다.
	 *
	 * 비어 있으면 빈 배열을 넣는다. null 로 두면 꺼내 쓸 때마다 "없는 경우" 를
	 * 따로 다뤄야 한다.
	 */
	private String writeSmallRoutines(List<SmallRoutineRequest> smallRoutines) {
		return objectMapper.writeValueAsString(
			smallRoutines == null ? List.of() : smallRoutines);
	}

	private RoutineTemplateResponse toResponse(RoutineTemplate template) {
		return new RoutineTemplateResponse(
			template.getId(), template.getTitle(),
			template.getStartTime(), template.getEndTime(), readSmallRoutines(template));
	}

}
