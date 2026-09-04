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
 * 핵심은 앱과 기기가 인증 방식이 완전히 달라서 SecurityFilterChain 을 두 개로
 * 나눠야 한다는 것이다. 앱은 보호자 JWT 를 쓰고 기기는 claim 때 발급한
 * opaque 토큰(내용이 없는 무작위 문자열 토큰)을 쓴다.
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
	@DisplayName("인증이 필요 없는 경로 4개는 토큰 없이도 401 이 아니다")
	@ValueSource(strings = {
		"/api/v1/auth/social-login",
		"/api/v1/auth/refresh",
		"/device-api/v1/claim",
		"/device-api/v1/token/refresh"
	})
	void whitelistedPathsAreNotRejected(String path) throws Exception {
		// "API.md" 1-1 이 정한 인증 불필요 엔드포인트 4개다.
		// 컨트롤러가 없어 404 가 나지만, 401 이 아니라는 것이 확인하려는 바다.
		mockMvc.perform(post(path))
			.andExpect(status().is(not(401)));
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
	@DisplayName("기기 경로에는 앱용 JWT 필터가 걸리지 않는다")
	void deviceChainDoesNotUseAppJwtFilter() throws Exception {
		// "API.md" 1-1 이 "/device-api/** 에는 JWT 필터가 걸리지 않아야 한다" 고 못박았다.
		// 형식이 깨진 JWT 를 보내도 앱 체인의 JWT 파싱 오류가 아니라
		// 기기 체인의 인증 실패로 처리되어야 한다.
		mockMvc.perform(post("/device-api/v1/sync")
				.header("Authorization", "Bearer 이건.JWT가.아니다"))
			.andExpect(status().isUnauthorized());
	}

}
