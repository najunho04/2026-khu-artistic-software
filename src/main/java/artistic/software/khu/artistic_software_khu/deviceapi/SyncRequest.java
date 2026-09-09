package artistic.software.khu.artistic_software_khu.deviceapi;

import java.time.LocalDate;
import java.util.List;

/**
 * 기기 동기화 요청. "API.md" 8장.
 *
 * push 와 pull 이 한 요청에 들어 있다. 나누면 기기가 그 사이에 꺼졌을 때
 * 올린 것은 반영됐는데 받은 것은 없는 어중간한 상태가 된다.
 */
public record SyncRequest(
	Integer battery,
	String firmware,
	List<CompletionRecord> completions,
	List<LocalDate> dates) {
}
