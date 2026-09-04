package artistic.software.khu.artistic_software_khu.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 예외 처리기가 "API.md" 2-2 실패 포맷대로 응답하는지 확인한다.
 *
 * 스프링 컨텍스트를 통째로 띄우지 않고 standaloneSetup 으로 컨트롤러와 예외
 * 처리기만 묶어 확인하는 이유는 두 가지다. 첫째, 검증 대상이 예외 처리기 하나라서
 * 컨텍스트 기동 비용을 낼 이유가 없다. 둘째, 보안 필터 체인(1-2)이 아직 없어서
 * 전체 컨텍스트를 띄우면 인증 때문에 요청이 막힌다.
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ExceptionThrowingTestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("비즈니스 예외는 매핑된 상태 코드와 code, message 로 나간다")
	void businessExceptionIsMappedToDocumentedErrorResponse() throws Exception {
		mockMvc.perform(get("/test/business-exception"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("자녀를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.error.details").doesNotExist());
	}

	@Test
	@DisplayName("검증 실패는 400 INVALID_INPUT 과 details 배열로 나간다")
	void validationFailureIsMappedToInvalidInputWithDetails() throws Exception {
		mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("입력값이 올바르지 않습니다."))
			.andExpect(jsonPath("$.error.details[0].field").value("name"))
			.andExpect(jsonPath("$.error.details[0].reason").exists());
	}

	@Test
	@DisplayName("매핑되지 않은 예외는 500 INTERNAL_ERROR 로 나간다")
	void unmappedExceptionIsMappedToInternalError() throws Exception {
		// 예상 못한 예외의 내부 메시지가 그대로 밖으로 나가면 안 된다.
		// 사용자에게는 정해진 한국어 문구만 보여준다.
		mockMvc.perform(get("/test/unexpected-exception"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.error.message").value("일시적인 오류가 발생했습니다."));
	}

	@Test
	@DisplayName("반환값이 없는 경우는 204 에 바디가 없다")
	void noContentResponseHasEmptyBody() throws Exception {
		// "API.md" 2-1 의 규칙이다. 삭제와 로그아웃이 여기에 해당한다.
		mockMvc.perform(get("/test/no-content"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
	}

	/**
	 * 이 테스트에서만 쓰는 컨트롤러. 예외 처리기를 자극하기 위한 용도이며
	 * 실제 API 가 아니므로 프로덕션 코드에 두지 않는다.
	 */
	@RestController
	static class ExceptionThrowingTestController {

		@GetMapping("/test/business-exception")
		void throwBusinessException() {
			throw new BusinessException(ErrorCode.CHILD_NOT_FOUND);
		}

		@GetMapping("/test/unexpected-exception")
		void throwUnexpectedException() {
			throw new IllegalStateException("밖으로 나가면 안 되는 내부 메시지");
		}

		@GetMapping("/test/no-content")
		ResponseEntity<Void> returnNoContent() {
			return ResponseEntity.noContent().build();
		}

		@PostMapping("/test/validation")
		void acceptValidatedBody(@Valid @RequestBody TestRequest request) {
		}

	}

	record TestRequest(@NotBlank String name) {
	}

}
