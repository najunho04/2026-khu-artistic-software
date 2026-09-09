package artistic.software.khu.artistic_software_khu.dashboard;

import artistic.software.khu.artistic_software_khu.device.DeviceResponse;
import java.util.List;

/**
 * 앱 메인 화면. "API.md" 11장.
 *
 * **새로 계산하는 것이 없다.** stats 세 번과 기기 목록을 합친 것이다.
 * 그런데도 따로 두는 이유는 앱이 메인 화면 하나를 그리려고 네 번 왕복하면
 * 화면이 순차적으로 그려져 로딩이 눈에 띄기 때문이다.
 *
 * devices 가 배열인 것이 breaking change 다. 기기가 여러 대일 때 단일 battery
 * 값이 어느 기기 것인지 표현할 수 없어 바꿨다. 앱과 동시 배포가 필요하다.
 */
public record DashboardResponse(
	Long childId,
	String childName,
	DashboardInsights insights,
	List<DeviceResponse> devices) {
}
