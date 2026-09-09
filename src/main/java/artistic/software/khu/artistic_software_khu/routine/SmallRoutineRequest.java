package artistic.software.khu.artistic_software_khu.routine;

/**
 * 빅루틴을 만들 때 함께 보내는 할 일 하나. "API.md" 9장.
 */
public record SmallRoutineRequest(String title, Integer order) {
}
