package artistic.software.khu.artistic_software_khu.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import artistic.software.khu.artistic_software_khu.auth.AuthenticationService;
import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import artistic.software.khu.artistic_software_khu.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "API.md" 1-1 의 인증 규약과 "ROADMAP.md" 1-2 의 보안 설정을 확인한다.
 *
 * 핵심은 앱과 기기가 인증 방식이 달라서 SecurityFilterChain 을 두 개로
 * 나눠야 한다는 것이다. 앱은 로그인 때 발급한 "access_uuid" 를 "X-Access-Uuid"
 * 헤더로 보내고, 기기는 페어링 때 발급한 "device_access_uuid" 를
 * "X-Device-Uuid" 헤더로 보낸다. 읽는 헤더도 조회하는 테이블도 다르다.
 *
 * 여기서 확인하는 것은 "경로별로 인증을 요구하는가" 하나다. 헤더 값으로 유저를
 * 제대로 찾는지는 실제 DB 를 띄우는 AuthenticationApiIntegrationTest 가 맡는다.
 * 나누어 둔 이유는 경로 규칙 하나 바꿀 때마다 컨테이너가 뜨기를 기다리지 않기
 * 위해서다.
 *
 * 화이트리스트 검증을 "401 이 아님" 으로 하는 이유는, 통과한 뒤에 무엇이
 * 나오는지가 경로마다 다르기 때문이다. 컨트롤러가 있으면 본문이 없어 400 이 나고
 * 없으면 404 가 난다. 어느 쪽이든 "필터를 통과했다" 는 뜻은 같다.
 */
@WebMvcTest
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	// 두 인증 필터가 이 저장소들을 필요로 한다. 여기서는 "가짜" 를 끼운다.
	//
	// 이 테스트가 확인하려는 것은 "경로별로 인증을 요구하는가" 이지
	// "헤더 값으로 유저를 제대로 찾는가" 가 아니기 때문이다. 뒤엣것은 실제 DB 를
	// 띄우는 AuthenticationApiIntegrationTest 가 확인한다. 여기서까지 DB 를
	// 띄우면 경로 규칙 하나 바꿀 때마다 컨테이너가 뜨기를 기다려야 한다.
	//
	// 가짜는 어떤 값을 물어도 "없음" 을 돌려주므로, 아래 테스트들은 전부
	// "인증되지 않은 요청" 으로 다뤄진다. 그것이 여기서 필요한 상태다.
	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private DeviceRepository deviceRepository;

	// 이 슬라이스는 컨트롤러를 전부 끌어오므로 그것들이 쓰는 서비스도 있어야 한다.
	// 여기서 실제 서비스가 필요하지는 않다. 확인하려는 것이 "요청이 컨트롤러까지
	// 가는가 마는가" 이지 컨트롤러가 무엇을 하는가가 아니기 때문이다.
	// 컨트롤러가 늘면 이 목록도 늘어난다.
	@MockitoBean
	private AuthenticationService authenticationService;

	@Test
	@DisplayName("토큰 없이 앱 API 를 호출하면 401 이고 공통 error 형태로 나간다")
	void appApiWithoutTokenReturnsUnauthorizedInCommonFormat() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
	}

	@ParameterizedTest
	@DisplayName("인증이 필요 없는 경로 3개는 헤더 없이도 401 이 아니다")
	@ValueSource(strings = {
		"/api/v1/auth/signup",
		"/api/v1/auth/login",
		"/device-api/v1/claim"
	})
	void whitelistedPathsAreNotRejected(String path) throws Exception {
		// "API.md" 1-1 이 정한 인증 불필요 엔드포인트 3개다.
		// 컨트롤러가 없어 404 가 나지만, 401 이 아니라는 것이 확인하려는 바다.
		mockMvc.perform(post(path))
			.andExpect(status().is(not(401)));
	}

	@ParameterizedTest
	@DisplayName("삭제된 경로는 더 이상 화이트리스트가 아니다")
	@ValueSource(strings = {
		"/api/v1/auth/social-login",
		"/api/v1/auth/refresh",
		"/device-api/v1/token/refresh"
	})
	void removedPathsAreNoLongerWhitelisted(String path) throws Exception {
		// 로그인 방식 변경과 기기 토큰 체계 삭제로 사라진 경로들이다.
		// 화이트리스트에 남아 있으면 존재하지 않는 경로가 인증 없이 열려 있는 셈이
		// 되고, 나중에 같은 경로에 다른 기능을 붙였을 때 무방비로 노출된다.
		mockMvc.perform(post(path))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("토큰 없이 기기 API 를 호출하면 401 이다")
	void deviceApiWithoutTokenReturnsUnauthorized() throws Exception {
		// 응답 "형태" 는 확인하지 않는다. 기기 API 에 공통 envelope 를 적용할지가
		// 아직 정해지지 않았기 때문이다 (ROADMAP 0-1). 상태 코드만 고정한다.
		mockMvc.perform(post("/device-api/v1/sync"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("기기 경로에는 앱용 인증 필터가 걸리지 않는다")
	void deviceChainDoesNotUseAppAuthenticationFilter() throws Exception {
		// "API.md" 1-1 이 "/device-api/** 에 앱용 필터가 걸리면 안 된다" 고 못박았다.
		// 앱용 헤더를 들고 기기 경로를 호출해도 통과되어서는 안 된다.
		// 앱용 필터가 기기 경로에 걸리면 기기 요청이 USERS 에서 유저를 찾다가 실패한다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header("X-Access-Uuid", "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d"))
			.andExpect(status().isUnauthorized());
	}

}
