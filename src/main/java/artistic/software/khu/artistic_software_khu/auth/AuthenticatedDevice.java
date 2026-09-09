package artistic.software.khu.artistic_software_khu.auth;

/**
 * 인증을 통과한 기기. SecurityContext 에 principal 로 담긴다.
 *
 * childId 를 함께 담는 이유는 기기 API 가 하는 일이 전부 "이 기기가 붙은
 * 자녀" 를 대상으로 하기 때문이다. 매번 기기에서 자녀를 다시 찾지 않게 한다.
 */
public record AuthenticatedDevice(Long deviceId, Long childId) {
}
