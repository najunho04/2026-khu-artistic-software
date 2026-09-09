package artistic.software.khu.artistic_software_khu.config;

import artistic.software.khu.artistic_software_khu.auth.AuthenticatedDevice;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 기기 인증 필터. "API.md" 1-1.
 *
 * 앱용 필터와 모양은 같지만 읽는 헤더도 조회하는 테이블도 다르다.
 * 이것이 두 체인을 나누는 이유다. 앱용 필터가 기기 경로에 걸리면
 * 기기 요청이 USERS 에서 유저를 찾다가 반드시 실패한다.
 */
public class DeviceAccessUuidAuthenticationFilter extends OncePerRequestFilter {

	// "API.md" 1-1 이 정한 기기 인증 헤더 이름.
	public static final String HEADER_NAME = "X-Device-Uuid";

	private final DeviceRepository deviceRepository;

	public DeviceAccessUuidAuthenticationFilter(DeviceRepository deviceRepository) {
		this.deviceRepository = deviceRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		parseUuid(request.getHeader(HEADER_NAME))
			.flatMap(deviceRepository::findByDeviceAccessUuidAndDeletedAtIsNull)
			.ifPresent(device -> {
				UsernamePasswordAuthenticationToken authentication =
					UsernamePasswordAuthenticationToken.authenticated(
						new AuthenticatedDevice(device.getId(), device.getChildId()),
						null,
						List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));

				SecurityContextHolder.getContext().setAuthentication(authentication);
			});

		filterChain.doFilter(request, response);
	}

	private Optional<UUID> parseUuid(String headerValue) {
		if (headerValue == null || headerValue.isBlank()) {
			return Optional.empty();
		}

		try {
			return Optional.of(UUID.fromString(headerValue));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

}
