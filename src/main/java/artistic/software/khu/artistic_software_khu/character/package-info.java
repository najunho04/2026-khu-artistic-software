/**
 * 캐릭터 도감과 자녀 보유 캐릭터. "API.md" 12장의 엔드포인트 2개를 담당한다.
 *
 * 성장 규칙은 "API.md" 12-1 에 있다. 기기 동기화로 새로 완료된 할 일 1개당
 * 경험치 1, 10마다 레벨 1, 최대 레벨 3, 경험치는 30 에서 멈춘다. 다 자란 뒤에
 * 경험치가 더 들어오면 도감의 code 오름차순으로 다음 캐릭터를 지급한다.
 * 확률 추첨이 아니므로 rarity 와 weight 는 쓰지 않는다.
 *
 * 구현 시점은 ROADMAP.md 6-2 이다.
 */
package artistic.software.khu.artistic_software_khu.character;
