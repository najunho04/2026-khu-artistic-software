package artistic.software.khu.artistic_software_khu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import tools.jackson.databind.ObjectMapper;

/**
 * 유저 API. "API.md" 5장. 로드맵 6-1 중 조회와 수정을 다룬다.
 *
 * 탈퇴("DELETE /users/me")는 "UserWithdrawalIntegrationTest" 에 따로 있다.
 * 탈퇴는 계정 한 줄이 아니라 자녀 · 기기 · 루틴 · 양식까지 함께 지우는
 * 동작이라, 조회 · 수정과 준비물이 다르다.
 *
 * "PATCH /users/me" 는 온보딩 1차(보호자 성명 입력)에서 쓰는 바로 그 API 다.
 * 별도 엔드포인트를 만들지 않는다는 로드맵 6-1 의 결정을 따른다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiIntegrationTest {

	private static final String APP_HEADER = "X-Access-Uuid";

	private static final String EMAIL = "user-api@example.com";

	private static final String PASSWORD = "password1234";

	private String accessUuid;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void signUpGuardian() throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email": "%s", "password": "%s"}""".formatted(EMAIL, PASSWORD)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		accessUuid = objectMapper.readTree(body).path("data").path("accessUuid").asText();
	}

	@Test
	@DisplayName("가입 직후 조회하면 이름이 비어 있다")
	void nameIsEmptyRightAfterSignUp() throws Exception {
		// 이름이 비어 있다는 것이 앱에게 "온보딩 1차를 아직 안 마쳤다" 는 신호다.
		mockMvc.perform(get("/api/v1/users/me").header(APP_HEADER, accessUuid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").isNumber())
			.andExpect(jsonPath("$.data.email").value(EMAIL))
			.andExpect(jsonPath("$.data.name").doesNotExist())
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());
	}

	@Test
	@DisplayName("응답에 provider 와 비밀번호와 접속 값이 들어가지 않는다")
	void responseNeverExposesSensitiveFields() throws Exception {
		// provider 는 소셜 로그인을 없애면서 항상 비게 되었다. 컬럼은 남겼지만
		// 응답에 담을 이유가 없다. 비밀번호 해시와 access_uuid 는 말할 것도 없다.
		// access_uuid 는 만료가 없어서 한 번 새어 나가면 회수할 방법이 없다.
		String body = mockMvc.perform(get("/api/v1/users/me").header(APP_HEADER, accessUuid))
			.andReturn().getResponse().getContentAsString();

		assertThat(body)
			.doesNotContain("provider")
			.doesNotContain("password")
			.doesNotContain("accessUuid");
	}

	@Test
	@DisplayName("성명을 입력하면 조회에 반영된다")
	void updatingNameIsReflected() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김보호"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("김보호"));

		mockMvc.perform(get("/api/v1/users/me").header(APP_HEADER, accessUuid))
			.andExpect(jsonPath("$.data.name").value("김보호"));
	}

	@Test
	@DisplayName("이름을 비워 보내면 400 이다")
	void blankNameIsRejected() throws Exception {
		// "API.md" 5장이 name 을 필수로 두었다. 빈 값을 그대로 받으면
		// 온보딩을 마친 것으로 보이는데 실제로는 이름이 없는 상태가 된다.
		mockMvc.perform(patch("/api/v1/users/me")
				.header(APP_HEADER, accessUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "  "}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("인증하지 않으면 쓸 수 없다")
	void userApiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name": "김보호"}"""))
			.andExpect(status().isUnauthorized());
	}

}
