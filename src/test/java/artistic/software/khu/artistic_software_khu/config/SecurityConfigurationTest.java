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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "API.md" 1-1 의 인증 규약과 "ROADMAP.md" 1-2 의 보안 설정을 확인한다.
 *
 * 핵심은 앱과 기기가 인증 방식이 달라서 SecurityFilterChain 을 두 개로
 * 나눠야 한다는 것이다. 앱은 로그인 때 발급한 "access_uuid" 를 "X-Access-Uuid"
 * 헤더로 보내고, 기기는 페어링 때 발급한 "device_access_uuid" 를
 * "X-Device-Uuid" 헤더로 보낸다. 읽는 헤더도 조회하는 테이블도 다르다.
 *
 * 컨트롤러가 아직 하나도 없는 상태에서 이 테스트가 성립하는 이유는, 보안 필터가
 * 컨트롤러보다 먼저 돌기 때문이다. 보호된 경로는 컨트롤러가 없어도 401 이 나가고,
 * 화이트리스트 경로는 필터를 통과한 뒤 컨트롤러가 없어서 404 가 난다.
 * 그래서 화이트리스트 검증은 "401 이 아님" 으로 확인한다.
 */
@WebMvcTest
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

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
