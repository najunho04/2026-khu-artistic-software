/**
 * 기기(펌웨어)가 직접 호출하는 API. "API.md" 8장의 엔드포인트 3개를 담당한다.
 *
 * 경로는 /device-api/v1/** 이고 인증은 보호자 JWT 가 아니라 claim 때 발급한
 * "opaque 토큰"(내용이 없는 무작위 문자열 토큰)을 DB 의 해시와 대조하는 방식이다.
 * 이 차이 때문에 앱용 device 패키지와 분리한다.
 *
 * claim 은 아직 토큰이 없는 상태로 호출되므로 화이트리스트에 들어간다.
 * token/refresh 는 재페어링 없이 복구되는 유일한 경로라서 기기 secret 이 필요하다.
 *
 * 구현 시점은 ROADMAP.md 2-2 와 4-1, 4-2 이다.
 */
package artistic.software.khu.artistic_software_khu.deviceapi;
