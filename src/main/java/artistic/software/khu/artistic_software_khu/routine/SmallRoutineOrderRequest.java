package artistic.software.khu.artistic_software_khu.routine;

/**
 * 스몰루틴 순서 변경 요청의 한 줄. "API.md" 9장 PUT .../small-routines/order.
 *
 * 요청 본문은 이 형태의 배열이다. 그 빅루틴의 할 일이 전부 담겨야 하고,
 * 하나라도 빠지면 거절한다. 일부만 보내면 나머지의 순서를 서버가 짐작해야
 * 하는데, 그 짐작이 사용자가 화면에서 본 것과 다를 수 있다.
 *
 * 배열에 담긴 차례가 아니라 "order" 값이 순서를 정한다. 앱의 드래그 정렬은
 * 화면에서 옮긴 결과를 숫자로 적어 보내기 때문이다.
 *
 * @param smallRoutineId 순서를 정할 할 일
 * @param order          몇 번째로 둘지. 비어 있으면 뒤로 가며 보낸 배열 차례를 지킨다
 */
public record SmallRoutineOrderRequest(Long smallRoutineId, Integer order) {
}
