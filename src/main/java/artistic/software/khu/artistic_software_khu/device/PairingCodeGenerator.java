package artistic.software.khu.artistic_software_khu.device;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 페어링 코드를 만든다. "API.md" 7장에서 숫자 10자리로 확정했다.
 *
 * **왜 10자리인가** — 사람이 손으로 입력하는 구간이 없기 때문이다. 앱이 핫스팟으로
 * 기기에 자동 전달하므로(14장 2단계) 길어도 사용자는 차이를 느끼지 못한다.
 * 반면 "POST /device-api/v1/claim" 은 인증이 없는 엔드포인트라 누구나 무작위로
 * 코드를 넣어볼 수 있다. 6자리(100만 조합)면 코드가 살아 있는 10분 안에 초당
 * 1600회로 전부 시도할 수 있고, 성공하면 남의 아이에게 공격자의 기기가 붙는다.
 * 10자리는 100억 조합이라 같은 시도가 사실상 불가능해진다.
 *
 * **왜 SecureRandom 인가** — 일반 난수(Random)는 값 몇 개만 관찰하면 다음 값을
 * 계산할 수 있다. 한 번 관찰당하면 이후 발급되는 코드가 전부 예측되므로,
 * 자릿수를 늘린 의미가 사라진다.
 */
@Component
public class PairingCodeGenerator {

	// 0000000000 ~ 9999999999 (10자리). int 로는 담기지 않아 long 을 쓴다.
	private static final long BOUND = 10_000_000_000L;

	// 앞자리 0 을 채우는 형식. 이것이 없으면 23 같은 짧은 값이 그대로 나가고,
	// 기기가 길이로 검사하는 순간 그런 코드만 실패한다. 드물게 일어나는
	// 실패라 원인을 찾기가 매우 어렵다.
	private static final String CODE_FORMAT = "%010d";

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		// nextLong(bound) 를 쓰면 범위를 넘지 않으면서 치우침도 없다.
		// nextLong() % BOUND 로 하면 값이 앞쪽으로 살짝 몰린다.
		return CODE_FORMAT.formatted(secureRandom.nextLong(BOUND));
	}

}
