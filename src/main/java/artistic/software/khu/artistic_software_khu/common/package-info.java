/**
 * 모든 도메인이 함께 쓰는 응답·예외 공통 계층.
 *
 * 담는 것은 "API.md" 2장 공통 응답 포맷(success / data / error 세 필드)과
 * 3장 에러 코드 표를 옮긴 ErrorCode 열거형, 그리고 전역 예외 처리기다.
 *
 * 이 패키지를 개별 도메인보다 먼저 잡는 이유는, 나중에 구현 대상 37개 API 의
 * 응답 형태를 한꺼번에 다시 손대는 일을 막기 위해서다.
 *
 * 구현 시점은 ROADMAP.md 1-2-1 이다.
 */
package artistic.software.khu.artistic_software_khu.common;
