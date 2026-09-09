package artistic.software.khu.artistic_software_khu.child;

import java.time.LocalDate;

/**
 * 자녀 등록 · 수정 요청. "API.md" 6장.
 *
 * 등록과 수정이 같은 record 를 쓰는 이유는 받는 필드가 같기 때문이다.
 * 다른 것은 "필수인가" 뿐이고, 그 판단은 서비스가 한다. 등록은 셋 다 필요하고
 * 수정은 보낸 것만 바꾼다.
 */
public record ChildRequest(String name, LocalDate birthDate, String relationship) {
}
