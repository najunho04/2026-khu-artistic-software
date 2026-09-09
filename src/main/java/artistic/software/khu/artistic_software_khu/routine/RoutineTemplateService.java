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

	private RoutineTemplate findOwnedTemplate(Long userId, Long templateId) {
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
		List<SmallRoutineRequest> smallRoutines = (template.getSmallRoutines() == null)
			? List.of()
			: objectMapper.readValue(
				template.getSmallRoutines(), new TypeReference<List<SmallRoutineRequest>>() {});

		return new RoutineTemplateResponse(
			template.getId(), template.getTitle(),
			template.getStartTime(), template.getEndTime(), smallRoutines);
	}

}
