package artistic.software.khu.artistic_software_khu.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * "API.md" 2장 공통 응답 포맷을 코드로 고정한다.
 *
 * 직렬화 결과를 Map 으로 되읽어 확인하는 이유는, 필드가 하나 빠지거나 이름이
 * 바뀌어도 자바 쪽에서는 컴파일이 그대로 되기 때문이다. 앱이 실제로 받는 모양을
 * 검증해야 의미가 있다. Map 은 삽입 순서를 유지하므로 필드 순서까지 확인할 수 있다.
 *
 * Spring Boot 4.1 은 Jackson 3 을 쓰므로 패키지가 "tools.jackson" 이다.
 * Jackson 2 의 "com.fasterxml.jackson.databind" 가 아니다.
 */
class ApiResponseTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@SuppressWarnings("unchecked")
	private Map<String, Object> serialize(ApiResponse<?> response) {
		return objectMapper.readValue(objectMapper.writeValueAsString(response), Map.class);
	}

	@Test
	@DisplayName("성공 응답은 success true, data 채움, error null 이다")
	void successResponseHasThreeFields() {
		Map<String, Object> json = serialize(ApiResponse.success("결과값"));

		assertThat(json).containsOnlyKeys("success", "data", "error");
		assertThat(json.get("success")).isEqualTo(true);
		assertThat(json.get("data")).isEqualTo("결과값");
		assertThat(json.get("error")).isNull();
	}

	@Test
	@DisplayName("실패 응답은 success false, data null, error 에 code 와 message 를 담는다")
	@SuppressWarnings("unchecked")
	void failureResponseCarriesErrorCodeAndMessage() {
		Map<String, Object> json = serialize(ApiResponse.failure(ErrorCode.CHILD_NOT_FOUND));

		assertThat(json.get("success")).isEqualTo(false);
		assertThat(json.get("data")).isNull();

		Map<String, Object> error = (Map<String, Object>) json.get("error");
		assertThat(error.get("code")).isEqualTo("CHILD_NOT_FOUND");
		assertThat(error.get("message")).isEqualTo("자녀를 찾을 수 없습니다.");
		// details 는 검증 실패 때만 채운다. 그 외에는 null 로 나가야 한다.
		assertThat(error.get("details")).isNull();
	}

	@Test
	@DisplayName("검증 실패 응답은 details 에 field 와 reason 을 담는다")
	@SuppressWarnings("unchecked")
	void validationFailureResponseCarriesFieldDetails() {
		Map<String, Object> json = serialize(ApiResponse.failure(
			ErrorCode.INVALID_INPUT,
			List.of(new FieldErrorDetail("birthDate", "미래 날짜는 사용할 수 없습니다."))
		));

		Map<String, Object> error = (Map<String, Object>) json.get("error");
		List<Map<String, Object>> details = (List<Map<String, Object>>) error.get("details");

		assertThat(error.get("code")).isEqualTo("INVALID_INPUT");
		assertThat(details).hasSize(1);
		assertThat(details.get(0)).containsOnlyKeys("field", "reason");
		assertThat(details.get(0).get("field")).isEqualTo("birthDate");
		assertThat(details.get(0).get("reason")).isEqualTo("미래 날짜는 사용할 수 없습니다.");
	}

	@Test
	@DisplayName("error 객체의 필드는 code, message, details 세 개다")
	@SuppressWarnings("unchecked")
	void errorObjectHasThreeFields() {
		Map<String, Object> json = serialize(ApiResponse.failure(ErrorCode.UNAUTHORIZED));

		assertThat((Map<String, Object>) json.get("error"))
			.containsOnlyKeys("code", "message", "details");
	}

}
