package artistic.software.khu.artistic_software_khu.config;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedUser;
import artistic.software.khu.artistic_software_khu.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 앱(보호자) 인증 필터. "API.md" 1-1.
 *
 * 하는 일은 "X-Access-Uuid" 헤더 값으로 USERS 에서 행 하나를 찾는 것이 전부다.
 * JWT 를 쓰지 않기로 해서 서명 검증도 만료 판정도 없다.
 *
 * 헤더가 없거나 값이 유효하지 않으면 인증을 "설정하지 않고" 그냥 넘긴다.
 * 여기서 직접 401 을 내보내지 않는 이유는, 그 경로가 인증이 필요한 곳인지
 * 아닌지를 이 필터가 알지 못하기 때문이다. 화이트리스트 경로라면 헤더가
 * 없는 것이 정상이다. 인증이 필요한데 없는 경우를 401 로 만드는 것은
 * SecurityConfiguration 의 authorizeHttpRequests 와 진입 지점의 몫이다.
 */
public class AccessUuidAuthenticationFilter extends OncePerRequestFilter {

	// "API.md" 1-1 이 정한 앱 인증 헤더 이름.
	public static final String HEADER_NAME = "X-Access-Uuid";

	private final UserRepository userRepository;

	public AccessUuidAuthenticationFilter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		parseUuid(request.getHeader(HEADER_NAME))
			.flatMap(userRepository::findByAccessUuidAndDeletedAtIsNull)
			.ifPresent(user -> {
				// 권한 목록을 비워 두지 않고 하나라도 넣는 이유는, 스프링 시큐리티가
				// 권한이 전혀 없는 인증을 "인증되지 않음" 과 구분하기 어렵게
				// 다루는 경우가 있기 때문이다. 역할 구분은 아직 없으므로
				// 모두에게 같은 값을 준다.
				UsernamePasswordAuthenticationToken authentication =
					UsernamePasswordAuthenticationToken.authenticated(
						new AuthenticatedUser(user.getId()),
						null,
						List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

				SecurityContextHolder.getContext().setAuthentication(authentication);
			});

		filterChain.doFilter(request, response);
	}

	/**
	 * 헤더 값을 UUID 로 바꾼다. 형식이 깨졌으면 비어 있는 값을 돌려준다.
	 *
	 * 형식 오류를 예외로 터뜨리지 않는 이유는, 그것이 서버의 잘못이 아니라
	 * 그냥 인증 실패이기 때문이다. 500 이 아니라 401 로 이어져야 한다.
	 */
	private java.util.Optional<UUID> parseUuid(String headerValue) {
		if (headerValue == null || headerValue.isBlank()) {
			return java.util.Optional.empty();
		}

		try {
			return java.util.Optional.of(UUID.fromString(headerValue));
		} catch (IllegalArgumentException exception) {
			return java.util.Optional.empty();
		}
	}

}
