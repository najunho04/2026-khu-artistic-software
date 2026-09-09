package artistic.software.khu.artistic_software_khu.user;

/**
 * 내 정보 수정 요청. "API.md" 5장.
 *
 * 온보딩 1차(보호자 성명 입력)도 이 API 를 쓴다. 같은 일을 하는 엔드포인트를
 * 두 개 두면 한쪽만 고치는 실수가 생긴다.
 */
public record UserUpdateRequest(String name) {
}
