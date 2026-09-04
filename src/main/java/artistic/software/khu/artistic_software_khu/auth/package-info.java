/**
 * 보호자 인증. "API.md" 4장의 엔드포인트 3개를 담당한다.
 *
 * POST /auth/social-login, POST /auth/refresh, POST /auth/logout 이며
 * 앞의 둘은 아직 토큰이 없는 상태로 호출되므로 보안 설정의 화이트리스트에 들어간다.
 *
 * 소셜 idToken 검증은 "인터페이스로 분리"한다. 실제 구현체는 구글·카카오를
 * 호출하지만, 단위 테스트와 CI 에서는 정해진 값을 돌려주는 Mock 을 끼운다.
 * CI 가 외부 네트워크를 타면 남의 서버 장애로 우리 빌드가 실패하기 때문이다.
 *
 * 구현 시점은 ROADMAP.md 1-4 이다.
 */
package artistic.software.khu.artistic_software_khu.auth;
