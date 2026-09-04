package artistic.software.khu.artistic_software_khu.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * "API.md" 1-3 날짜·시간 포맷을 코드로 고정한다.
 *
 * 포맷을 테스트로 묶어두는 이유는 앱과 기기가 이 문자열을 그대로 파싱하기 때문이다.
 * 초 단위가 하나 붙거나 빠지는 것만으로도 기기 펌웨어의 파싱이 깨질 수 있는데,
 * 서버 쪽에서는 아무 오류도 나지 않아 알아차리기 어렵다.
 *
 * 시각 정책은 "ERD.md" 5-2 에서 확정한 표준 UTC 방식이다. 서버는 UTC 로 저장하고
 * 앱과 기기가 표시 시점에 KST 로 바꾼다. 다만 time 은 "KST 벽시계 시각" 이라
 * 시간대 정보를 붙이지 않는다.
 */
class JsonTimeFormatModuleTest {

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JsonTimeFormatModule())
		.build();

	@Test
	@DisplayName("date 는 yyyy-MM-dd 로 나간다")
	void localDateIsSerializedAsIsoDate() {
		assertThat(objectMapper.writeValueAsString(LocalDate.of(2026, 9, 1)))
			.isEqualTo("\"2026-09-01\"");
	}

	@Test
	@DisplayName("time 은 초를 붙이지 않고 HH:mm 으로 나간다")
	void localTimeIsSerializedWithoutSeconds() {
		// 기본 동작은 초가 0 이 아니면 초까지 붙는다. 문서는 HH:mm 만 쓰므로 잘라낸다.
		assertThat(objectMapper.writeValueAsString(LocalTime.of(8, 30)))
			.isEqualTo("\"08:30\"");
		assertThat(objectMapper.writeValueAsString(LocalTime.of(8, 30, 15)))
			.isEqualTo("\"08:30\"");
	}

	@Test
	@DisplayName("timestamp 는 ISO-8601 UTC 로 나간다")
	void instantIsSerializedAsIsoUtc() {
		// 숫자(에포크 초)로 나가면 안 된다. 문서 예시는 2026-09-01T08:30:00Z 형태다.
		assertThat(objectMapper.writeValueAsString(Instant.parse("2026-09-01T08:30:00Z")))
			.isEqualTo("\"2026-09-01T08:30:00Z\"");
	}

	@Test
	@DisplayName("HH:mm 문자열을 time 으로 되읽을 수 있다")
	void localTimeIsDeserializedFromHourAndMinute() {
		// 앱이 보낸 startTime 을 서버가 받아야 하므로 역방향도 성립해야 한다.
		assertThat(objectMapper.readValue("\"08:30\"", LocalTime.class))
			.isEqualTo(LocalTime.of(8, 30));
	}

}
