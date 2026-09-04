package artistic.software.khu.artistic_software_khu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 필터 체인 설정. "API.md" 1-1 과 "ROADMAP.md" 1-2 를 따른다.
 *
 * 체인을 두 개로 나누는 것이 이 클래스의 핵심이다. 앱(보호자)은 JWT 로 인증하고
 * 기기는 claim 때 발급한 opaque 토큰(내용이 없는 무작위 문자열 토큰)을 DB 의
 * 해시와 대조해 인증한다. 검증 방법이 아예 달라서 한 체인에 섞으면 한쪽 방식이
 * 다른 쪽 경로에도 적용된다. "API.md" 1-1 도 "/device-api/** 에는 JWT 필터가
 * 걸리지 않아야 한다" 고 명시하고 있다.
 *
 * 두 체인 모두 세션을 만들지 않는다. 앱과 기기 모두 매 요청에 토큰을 실어 보내는
 * 방식이라 서버가 로그인 상태를 들고 있을 이유가 없기 때문이다. 세션이 없으므로
 * CSRF 보호도 끈다. CSRF 공격은 브라우저가 쿠키를 자동으로 실어 보내는 성질을
 * 이용하는 것인데, 여기서는 쿠키를 쓰지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	// "API.md" 1-1 의 인증 불필요 엔드포인트 중 앱 쪽 2개.
	// 아직 토큰이 없는 상태에서 호출되므로 인증을 걸 수 없다.
	private static final String[] APP_PUBLIC_PATHS = {
		"/api/v1/auth/social-login",
		"/api/v1/auth/refresh"
	};

	// "API.md" 1-1 의 인증 불필요 엔드포인트 중 기기 쪽 2개.
	// claim 은 토큰을 발급받기 전이고, token/refresh 는 토큰이 만료·손상됐을 때
	// 재페어링 없이 복구되는 유일한 경로다.
	private static final String[] DEVICE_PUBLIC_PATHS = {
		"/device-api/v1/claim",
		"/device-api/v1/token/refresh"
	};

	/**
	 * 기기용 체인. 경로는 /device-api/v1/** 이다.
	 *
	 * 앱용보다 먼저 평가되도록 순서를 앞에 둔다. 두 체인의 담당 경로가 겹치지는
	 * 않지만, 순서를 명시해 두면 나중에 경로가 늘어나도 의도가 남는다.
	 *
	 * JWT 필터를 "달지 않는" 것이 이 체인의 요점이다.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain deviceApiSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/device-api/v1/**")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(DEVICE_PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			// 기기 API 에 공통 envelope 를 적용할지가 아직 정해지지 않아
			// (ROADMAP 0-1) 응답 본문 없이 상태 코드만 내보낸다.
			// 결정이 나오면 여기에 본문을 쓰는 진입점을 붙인다.
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

		return http.build();
	}

	/**
	 * 앱용 체인. 경로는 /api/v1/** 이다.
	 *
	 * 인증 실패 응답은 "API.md" 2-2 의 공통 error 형태로 나가야 하므로
	 * 공통 응답 계층과 연결된 진입점을 붙인다.
	 */
	@Bean
	@Order(2)
	SecurityFilterChain appSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
		throws Exception {

		http
			.securityMatcher("/api/v1/**")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(APP_PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(new CommonAuthenticationEntryPoint(objectMapper)));

		return http.build();
	}

}
