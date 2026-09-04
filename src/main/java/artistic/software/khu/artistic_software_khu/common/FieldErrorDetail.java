package artistic.software.khu.artistic_software_khu.common;

/**
 * 검증 실패 시 어떤 필드가 왜 틀렸는지를 담는 한 건의 상세 정보.
 *
 * "API.md" 2-2 의 details 배열 원소 형태를 그대로 옮긴 것이다.
 * 예를 들어 birthDate 에 미래 날짜가 들어오면
 * field 는 "birthDate", reason 은 "미래 날짜는 사용할 수 없습니다." 가 된다.
 *
 * record 로 만든 이유는 값만 담고 동작이 없기 때문이며,
 * 선언한 순서가 그대로 JSON 필드 순서가 된다.
 *
 * @param field  문제가 된 요청 필드 이름. API 필드이므로 camelCase 를 쓴다
 * @param reason 사용자에게 보여줄 한국어 사유
 */
public record FieldErrorDetail(String field, String reason) {
}
