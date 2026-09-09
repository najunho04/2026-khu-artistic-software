package artistic.software.khu.artistic_software_khu.dashboard;

import artistic.software.khu.artistic_software_khu.child.Child;
import artistic.software.khu.artistic_software_khu.child.ChildService;
import artistic.software.khu.artistic_software_khu.device.DeviceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성취도 통계와 대시보드. "API.md" 11장.
 *
 * 기간 계산을 StatsPeriod 한 곳에 맡기는 것이 이 서비스의 요점이다. 세
 * 엔드포인트가 같은 규칙을 써야 하는데, 각자 날짜를 계산하면 대시보드의 주간
 * 수치와 "stats?period=WEEK" 가 다르게 나올 수 있다. 그 차이는 보통 하루짜리라
 * 눈으로는 알아차리기 어렵다.
 */
@Service
public class StatsService {

	// "ERD.md" 5-2 — 날짜 경계는 KST 기준이다. 서버는 UTC 로 돌지만
	// "오늘" 은 사용자가 사는 곳의 오늘이어야 한다.
	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final StatsRepository statsRepository;

	private final ChildService childService;

	private final DeviceService deviceService;

	private final Clock clock;

	public StatsService(
		StatsRepository statsRepository,
		ChildService childService,
		DeviceService deviceService,
		Clock clock) {

		this.statsRepository = statsRepository;
		this.childService = childService;
		this.deviceService = deviceService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public StatsSummary findStats(Long userId, Long childId, String periodName) {
		childService.findOwnedChild(userId, childId);

		return summarize(childId, StatsPeriod.of(periodName, today()));
	}

	@Transactional(readOnly = true)
	public List<MissionStatsResponse> findMissionStats(
		Long userId, Long childId, String periodName) {

		childService.findOwnedChild(userId, childId);

		StatsPeriod period = StatsPeriod.of(periodName, today());

		return statsRepository.countByMission(childId, period.from(), period.to()).stream()
			.map(projection -> new MissionStatsResponse(
				projection.getSeriesId(),
				projection.getTitle(),
				projection.getDoneCount(),
				projection.getTotalCount(),
				StatsSummary.rateOf(projection.getDoneCount(), projection.getTotalCount())))
			.toList();
	}

	@Transactional(readOnly = true)
	public DashboardResponse findDashboard(Long userId, Long childId) {
		Child child = childService.findOwnedChild(userId, childId);

		LocalDate today = today();

		// 세 구간을 각각 센다. 새로 계산하는 규칙은 없고 stats 를 세 번 부르는
		// 것과 같다. 같은 StatsPeriod 를 쓰므로 stats 엔드포인트와 값이 어긋날
		// 수 없다.
		DashboardInsights insights = new DashboardInsights(
			summarize(childId, StatsPeriod.of("DAY", today)),
			summarize(childId, StatsPeriod.of("WEEK", today)),
			summarize(childId, StatsPeriod.of("MONTH", today)));

		return new DashboardResponse(
			child.getId(),
			child.getName(),
			insights,
			deviceService.findByChild(userId, childId));
	}

	private StatsSummary summarize(Long childId, StatsPeriod period) {
		StatsRepository.CountProjection counts =
			statsRepository.countIn(childId, period.from(), period.to());

		return StatsSummary.of(period, counts.getDoneCount(), counts.getTotalCount());
	}

	/**
	 * KST 기준 오늘. 통계의 모든 기간이 이 날짜를 끝점으로 삼는다.
	 */
	private LocalDate today() {
		return LocalDate.now(clock.withZone(KOREA_ZONE));
	}

}
