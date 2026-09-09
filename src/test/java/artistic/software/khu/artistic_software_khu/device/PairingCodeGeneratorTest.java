package artistic.software.khu.artistic_software_khu.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 페어링 코드 생성기. "API.md" 7장에서 숫자 10자리로 확정했다.
 *
 * 이 조각을 따로 두는 이유는 여기서 정하는 것이 "어떤 문자열을 만드는가" 하나뿐이라
 * DB 없이 전부 검증할 수 있기 때문이다. 저장과 충돌 처리는 서비스가 맡는다.
 *
 * 확인하려는 것은 두 가지다. **길이가 항상 10인가**와 **값을 예측할 수 없는가**.
 * 앞엣것이 깨지면 기기 쪽 파싱이 어긋나고, 뒤엣것이 깨지면 남의 아이에게
 * 공격자의 기기가 붙는다.
 */
class PairingCodeGeneratorTest {

	private final PairingCodeGenerator generator = new PairingCodeGenerator();

	@Test
	@DisplayName("항상 숫자 10자리를 만든다")
	void alwaysGeneratesTenDigits() {
		// 한 번만 보고 넘어가면 "우연히 10자리였던 경우" 를 통과시킨다.
		// 앞자리가 0 인 값은 1000번에 100번쯤 나오므로 이 횟수면 반드시 섞인다.
		IntStream.range(0, 1000).forEach(attempt -> {
			String code = generator.generate();

			assertThat(code).hasSize(10);
			assertThat(code).matches("\\d{10}");
		});
	}

	@Test
	@DisplayName("앞자리가 0 인 코드도 열 자리를 지킨다")
	void leadingZeroIsPreserved() {
		// 정수로 다루면 0000000023 이 23 이 되어 자릿수가 흔들린다.
		// 기기가 길이로 검사하는 순간 그런 코드만 실패하는데, 1000번에 한 번쯤
		// 일어나는 일이라 원인을 찾기가 매우 어렵다.
		boolean sawLeadingZero = IntStream.range(0, 2000)
			.mapToObj(attempt -> generator.generate())
			.anyMatch(code -> code.startsWith("0"));

		assertThat(sawLeadingZero)
			.as("2000번 안에 앞자리 0 인 코드가 한 번도 안 나오면 0 을 버리고 있다는 뜻이다")
			.isTrue();
	}

	@Test
	@DisplayName("연달아 만든 코드가 서로 다르다")
	void generatedCodesAreDistinct() {
		// 100억 조합에서 1000개를 뽑으면 겹칠 확률이 사실상 없다.
		// 겹친다면 난수를 쓰지 않고 순번이나 고정값을 쓰고 있다는 뜻이다.
		Set<String> codes = new HashSet<>();

		IntStream.range(0, 1000).forEach(attempt -> codes.add(generator.generate()));

		assertThat(codes).hasSize(1000);
	}

	@Test
	@DisplayName("생성기를 새로 만들어도 같은 값이 반복되지 않는다")
	void separateGeneratorsDoNotRepeat() {
		// 씨앗값을 고정해 두면 서버를 다시 띄울 때마다 같은 코드가 같은 순서로
		// 나온다. 한 번 관찰당하면 이후 발급되는 코드가 전부 예측된다.
		Set<String> codes = new HashSet<>();

		IntStream.range(0, 100).forEach(attempt ->
			codes.add(new PairingCodeGenerator().generate()));

		assertThat(codes).hasSize(100);
	}

	@Test
	@DisplayName("가능한 값의 범위 전체를 쓴다")
	void usesWholeRange() {
		// 첫 자리가 늘 같은 값이면 실제 조합 수가 10분의 1로 줄어든다.
		// 100억이 10억이 되는 셈인데, 겉으로는 10자리라 알아차리기 어렵다.
		Set<Character> firstDigits = new HashSet<>();

		IntStream.range(0, 2000).forEach(attempt ->
			firstDigits.add(generator.generate().charAt(0)));

		assertThat(firstDigits)
			.as("2000번을 뽑았는데 첫 자리에 나온 숫자가 몇 종류뿐이면 범위가 좁다는 뜻이다")
			.hasSizeGreaterThanOrEqualTo(9);
	}

}
