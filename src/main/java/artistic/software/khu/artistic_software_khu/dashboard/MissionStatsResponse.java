package artistic.software.khu.artistic_software_khu.dashboard;

import java.util.UUID;

/**
 * 미션 하나의 성취도. "API.md" 11장 GET /stats/missions.
 *
 * "미션" 은 series_id 로 묶인 할 일들이다. 같은 "양치하기" 가 여러 날에 걸쳐
 * 있어도 하나로 센다. 이름을 바꿔도 series_id 는 그대로라 한 행으로 남고,
 * title 은 가장 최근 값을 쓴다. 이름을 고쳤다고 통계가 두 갈래로 갈라지면
 * "양치하기 미션을 얼마나 하고 있나" 를 알 수 없게 된다.
 */
public record MissionStatsResponse(
	UUID seriesId,
	String title,
	int doneCount,
	int totalCount,
	double completionRate) {
}
