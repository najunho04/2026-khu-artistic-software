package artistic.software.khu.artistic_software_khu.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 주는 빈.
 *
 * 코드 안에서 Instant.now() 를 직접 부르지 않고 이것을 주입받는 이유는,
 * 그렇게 해야 테스트에서 시각을 고정할 수 있기 때문이다. 직접 부르면
 * "만료 시각이 지났을 때" 같은 상황을 검증할 방법이 없어진다.
 *
 * UTC 로 고정한다. "ERD.md" 5-2 가 정한 표준 방식이며, 서버는 UTC 로 저장하고
 * 앱과 기기가 표시 시점에 KST 로 바꾼다. 다만 "오늘 날짜" 를 따질 때는
 * KST 기준이어야 하므로, 날짜가 필요한 곳에서는 이 시각을 KST 로 옮겨 쓴다.
 */
@Configuration
public class ClockConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
