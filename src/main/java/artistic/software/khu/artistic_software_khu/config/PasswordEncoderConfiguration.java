package artistic.software.khu.artistic_software_khu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 방식. "API.md" 15장에서 bcrypt 로 확정했다.
 *
 * bcrypt 를 고른 이유는 spring-boot-starter-security 에 이미 들어 있어
 * 의존성이 하나도 늘지 않기 때문이다. 강도는 기본값(10)을 쓴다.
 *
 * bcrypt 는 같은 비밀번호를 넣어도 매번 다른 결과가 나온다. 무작위 값(salt) 을
 * 섞어 결과 문자열 안에 함께 담기 때문이다. 그래서 별도의 salt 컬럼이 필요 없고
 * "password_hash" 컬럼 하나면 된다. 대조할 때는 저장된 문자열에서 salt 를
 * 꺼내 쓰므로 matches 로 확인해야 하고, 두 해시를 문자열 비교하면 안 된다.
 */
@Configuration
public class PasswordEncoderConfiguration {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
