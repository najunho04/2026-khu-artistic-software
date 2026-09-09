package artistic.software.khu.artistic_software_khu.auth;

/**
 * 인증을 통과한 보호자. SecurityContext 에 principal 로 담긴다.
 *
 * 유저 객체 전체가 아니라 id 만 담는 이유는, 필터가 조회한 엔티티를 그대로
 * 들고 다니면 요청 내내 그 값이 최신인지 신경 써야 하기 때문이다.
 * id 만 있으면 필요한 곳에서 그때 다시 조회하면 된다.
 */
public record AuthenticatedUser(Long userId) {
}
