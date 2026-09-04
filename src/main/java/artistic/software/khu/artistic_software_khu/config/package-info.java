/**
 * 애플리케이션 전역 설정.
 *
 * 가장 중요한 것은 SecurityFilterChain 을 "두 개로 분리"하는 설정이다.
 * 앱용 /api/v1/** 는 보호자 JWT 로 인증하고, 기기용 /device-api/v1/** 는
 * opaque 토큰(내용이 없는 무작위 문자열 토큰)을 DB 의 token_hash 와 대조해
 * 인증한다. 인증 방식이 서로 다르기 때문에 하나의 체인에 섞으면 안 된다.
 *
 * 날짜·시간 직렬화 포맷 고정도 여기에 둔다. 저장은 UTC(timestamptz)이고
 * time 컬럼은 KST 벽시계 시각으로 해석한다.
 *
 * 구현 시점은 ROADMAP.md 1-2 이다.
 */
package artistic.software.khu.artistic_software_khu.config;
