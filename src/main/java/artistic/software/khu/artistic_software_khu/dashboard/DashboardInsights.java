package artistic.software.khu.artistic_software_khu.dashboard;

/**
 * 대시보드가 보여주는 세 구간 성취도. "API.md" 11장.
 *
 * 이전에는 "양치하기 미션 이행률이 낮아요" 같은 **문구형 인사이트**를 담을
 * 자리였다. 종류와 생성 규칙이 정해지지 않아 Phase 5 전체를 막고 있었고,
 * 2026-09-09 에 하루 · 일주일 · 한달 성취도 셋을 담는 것으로 바꿨다.
 */
public record DashboardInsights(StatsSummary day, StatsSummary week, StatsSummary month) {
}
