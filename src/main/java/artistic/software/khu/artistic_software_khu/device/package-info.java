/**
 * 앱이 호출하는 기기 관리 API. "API.md" 7장의 엔드포인트 5개를 담당한다.
 *
 * 경로는 /api/v1/devices/** 와 /api/v1/children/{childId}/devices 이고
 * 인증은 "보호자 JWT" 를 쓴다. 기기 자신이 호출하는 deviceapi 패키지와는
 * 인증 방식이 다르므로 반드시 분리해서 둔다.
 *
 * 페어링 코드 발급과 완료 폴링이 여기에 속한다.
 * 구현 시점은 ROADMAP.md 2-2 와 4-3 이다.
 */
package artistic.software.khu.artistic_software_khu.device;
