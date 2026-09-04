/**
 * 자녀 프로필. "API.md" 6장의 엔드포인트 5개를 담당한다.
 *
 * 소유권 검사(요청한 보호자의 자녀가 맞는지)를 "한 곳에 모아" 두고 자녀와
 * 관련된 모든 API 가 같은 함수를 부르게 한다. 이 검사가 엔드포인트마다
 * 흩어지면 403 동작이 API 별로 달라진다.
 *
 * 구현 시점은 ROADMAP.md 2-1 이다.
 */
package artistic.software.khu.artistic_software_khu.child;
