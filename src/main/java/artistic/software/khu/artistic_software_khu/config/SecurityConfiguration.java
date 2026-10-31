package artistic.software.khu.artistic_software_khu.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import artistic.software.khu.artistic_software_khu.common.ErrorCode;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import artistic.software.khu.artistic_software_khu.user.UserRepository;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 필터 체인 설정. "API.md" 1-1 과 "ROADMAP.md" 1-2 를 따른다.
 *
 * 체인을 두 개로 나누는 것이 이 클래스의 핵심이다. 앱(보호자)은 로그인 때 발급한
 * "access_uuid" 를 "X-Access-Uuid" 헤더로 보내고 서버는 그 값으로 USERS 를 찾는다.
 * 기기는 페어링 때 발급한 "device_access_uuid" 를 "X-Device-Uuid" 헤더로 보내고
 * 서버는 그 값으로 DEVICES 를 찾는다. 읽는 헤더도 조회하는 테이블도 달라서
 * 한 체인에 섞으면 한쪽 방식이 다른 쪽 경로에도 적용된다. 앱용 필터가 기기 경로에
 * 걸리면 기기 요청이 USERS 에서 유저를 찾다가 반드시 실패한다.
 *
 * JWT 는 쓰지 않는다. 서명 검증도 만료 판정도 재발급도 없고, 헤더 값으로 행 하나를
 * 찾는 것이 인증의 전부다. 그 대가로 값이 새어 나가면 로그아웃 외에 되찾을 방법이
 * 없다. 공모전 범위에서 알고 받아들인 선택이다. ("API.md" 1-1)
 *
 * 두 체인 모두 세션을 만들지 않는다. 매 요청에 값을 실어 보내는 방식이라 서버가
 * 로그인 상태를 들고 있을 이유가 없기 때문이다. 세션이 없으므로 CSRF 보호도 끈다.
 * CSRF 공격은 브라우저가 쿠키를 자동으로 실어 보내는 성질을 이용하는 것인데,
 * 여기서는 쿠키를 쓰지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	// "API.md" 1-1 의 인증 불필요 엔드포인트 중 앱 쪽 2개.
	// 둘 다 아직 access_uuid 를 받기 전에 호출되므로 인증을 걸 수 없다.
	// signup 과 login 이 나뉘어 있는 것은 소셜 로그인과 달리 가입과 로그인이
	// 별개의 행위이기 때문이다.
	private static final String[] APP_PUBLIC_PATHS = {
		"/api/v1/auth/signup",
		"/api/v1/auth/login"
	};

	// "API.md" 1-1 의 인증 불필요 엔드포인트 중 기기 쪽 1개.
	// claim 은 device_access_uuid 를 발급받는 바로 그 호출이라 인증을 걸 수 없다.
	//
	// 예전에는 "/device-api/v1/token/refresh" 도 여기 있었다. 기기 토큰 체계를
	// 없애면서 그 경로 자체가 삭제되어 화이트리스트에서도 뺐다. 사라진 경로를
	// 화이트리스트에 남겨두면 나중에 같은 주소에 다른 기능을 붙였을 때
	// 아무 인증 없이 열리게 된다.
	private static final String[] DEVICE_PUBLIC_PATHS = {
		"/device-api/v1/claim"
	};

	/**
	 * 로그에서 값을 가리는 규칙. 두 체인의 로깅 필터가 같은 것을 나눠 쓴다.
	 *
	 * 체인마다 따로 만들지 않는 이유는 가리는 규칙이 앱과 기기에서 다를 이유가
	 * 없기 때문이다. 하나만 두면 규칙을 고칠 때 한 쪽만 고치는 실수가 없다.
	 */
	@Bean
	SensitiveValueMasker sensitiveValueMasker() {
		return new SensitiveValueMasker();
	}

	/**
	 * 브라우저 호출 허용 규칙 (CORS). "API.md" 1-1-2, 2026-10-31 확정.
	 *
	 * CORS 는 브라우저가 "다른 주소에서 온 페이지가 이 서버를 불러도 되는가" 를
	 * 서버에 묻고, 서버가 허락한 경우에만 응답을 페이지에 넘기는 규칙이다.
	 * 앱과 실제 기기는 브라우저가 아니라서 이 규칙과 상관이 없다. 지금 이것이 필요한
	 * 것은 GitHub Pages 에서 도는 가상 기기 페이지 하나뿐이다.
	 *
	 * 두 체인이 같은 규칙을 나눠 쓴다. 허용 출처를 체인마다 따로 두면 한쪽만
	 * 고치는 실수가 생기기 때문이다.
	 *
	 * 허용 출처를 "*" 로 열지 않는다. 그러면 아무 웹페이지나 방문자의 브라우저를
	 * 빌려 서버를 부를 수 있다. 목록은 설정값 "yeso.cors.allowed-origins" 로 두어
	 * 출처가 늘어도 코드를 고치지 않게 했다.
	 *
	 * 쿠키를 쓰지 않으므로 allowCredentials 는 켜지 않는다. 인증 값은 헤더로만 오간다.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(
		@Value("${yeso.cors.allowed-origins}") List<String> allowedOrigins) {

		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.setAllowedOrigins(allowedOrigins);
		corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		// 앱과 기기의 인증 헤더 두 개와 JSON 본문용 Content-Type 만 허락한다.
		// 여기 없는 헤더를 실은 요청은 브라우저가 보내지 않는다.
		corsConfiguration.setAllowedHeaders(List.of("Content-Type", "X-Access-Uuid", "X-Device-Uuid"));
		// 미리 묻는 요청(preflight)의 답을 브라우저가 1시간 기억하게 한다.
		// 기기 페이지는 동기화를 반복하므로 매번 한 번 더 묻는 왕복을 줄인다.
		corsConfiguration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);
		return source;
	}

	/**
	 * 기기용 체인. 경로는 /device-api/v1/** 이다.
	 *
	 * 앱용보다 먼저 평가되도록 순서를 앞에 둔다. 두 체인의 담당 경로가 겹치지는
	 * 않지만, 순서를 명시해 두면 나중에 경로가 늘어나도 의도가 남는다.
	 *
	 * 앱용 필터를 "달지 않는" 것이 이 체인의 요점이다. 대신 기기용 필터를 단다.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain deviceApiSecurityFilterChain(
		HttpSecurity http,
		ObjectMapper objectMapper,
		DeviceRepository deviceRepository,
		SensitiveValueMasker sensitiveValueMasker) throws Exception {

		http
			.securityMatcher("/device-api/v1/**")
			// CORS 를 인증보다 먼저 처리한다. 미리 묻는 요청(preflight)은 브라우저
			// 규칙상 인증 헤더를 싣지 않으므로, 인증 뒤에 두면 401 로 막혀 본 요청이
			// 나가지 못한다. 규칙은 위 corsConfigurationSource 빈을 쓴다.
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(DEVICE_PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			// 기기 API 도 앱과 같은 공통 envelope 를 쓴다(2026-09-09 확정, 0-1 해소).
			// 다만 에러 코드는 DEVICE_UNAUTHORIZED 로 다르다. 기기는 이 코드를
			// 받으면 스스로 복구할 방법이 없어 재페어링을 안내해야 하는데,
			// 앱과 같은 코드를 쓰면 그 구분이 사라진다.
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(
					new CommonAuthenticationEntryPoint(objectMapper, ErrorCode.DEVICE_UNAUTHORIZED)))
			// 인증 정보를 읽는 자리에 우리 필터를 끼운다. 아이디와 비밀번호를
			// 받는 기본 필터 자리에 넣는 이유는 그 지점이 "요청에서 신원을
			// 알아내는" 단계이기 때문이다. 그 필터 자체는 쓰지 않는다.
			.addFilterBefore(
				new DeviceAccessUuidAuthenticationFilter(deviceRepository),
				UsernamePasswordAuthenticationFilter.class)
			// 로깅은 인증보다 "앞" 에 둔다. 인증에 실패한 요청도 기록에 남아야
			// 하기 때문이다. 뒤에 두면 401 로 끊긴 요청이 로그에 아예 안 남아
			// "기기가 연결이 안 된다" 는 신고가 들어왔을 때 볼 것이 없다.
			.addFilterBefore(
				new RequestLoggingFilter(sensitiveValueMasker),
				DeviceAccessUuidAuthenticationFilter.class);

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
	SecurityFilterChain appSecurityFilterChain(
		HttpSecurity http,
		ObjectMapper objectMapper,
		UserRepository userRepository,
		SensitiveValueMasker sensitiveValueMasker) throws Exception {

		http
			.securityMatcher("/api/v1/**")
			// 기기용 체인과 같은 이유로 인증보다 먼저 CORS 를 처리한다.
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(APP_PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(
					new CommonAuthenticationEntryPoint(objectMapper, ErrorCode.UNAUTHORIZED)))
			.addFilterBefore(
				new AccessUuidAuthenticationFilter(userRepository),
				UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(
				new RequestLoggingFilter(sensitiveValueMasker),
				AccessUuidAuthenticationFilter.class);

		return http.build();
	}

}
