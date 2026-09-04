package artistic.software.khu.artistic_software_khu.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 전체가 쓰는 JSON 변환기 설정.
 *
 * "API.md" 1-3 이 정한 날짜·시간 포맷을 모든 응답에 일괄 적용하기 위한 것이다.
 * 개별 DTO 마다 포맷 애노테이션을 붙이는 방법도 있지만, 그렇게 하면 붙이는 것을
 * 잊은 필드 하나가 다른 모양으로 나가고 그것을 알아차릴 방법이 없다.
 *
 * Spring Boot 4.1 은 Jackson 3 을 쓰므로 확장점이
 * JsonMapperBuilderCustomizer 다. Jackson 2 시절의
 * Jackson2ObjectMapperBuilderCustomizer 가 아니다.
 */
@Configuration
public class JacksonConfiguration {

	@Bean
	JsonMapperBuilderCustomizer jsonTimeFormatCustomizer() {
		return builder -> builder.addModule(new JsonTimeFormatModule());
	}

}
