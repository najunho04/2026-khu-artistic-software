package artistic.software.khu.artistic_software_khu.child;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import artistic.software.khu.artistic_software_khu.device.DeviceRepository;
import artistic.software.khu.artistic_software_khu.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 자녀 생년월일의 "미래 날짜" 판정이 어느 시간대를 기준으로 하는지 확인한다.
 *
 * "API.md" 1-3 과 "ERD.md" 5-2 는 날짜 경계를 KST 로 정했다. 서버는 UTC 로
 * 돌지만 "오늘" 은 사용자가 사는 곳의 오늘이어야 한다.
 *
 * 이 차이는 한국 시각 00시부터 09시 사이에만 드러난다. 그 시간대에는 UTC 가
 * 아직 어제이므로, 보호자가 화면에서 고른 "오늘" 이 서버에게는 내일로 보인다.
 * 새벽에만 자녀 등록이 실패하고 아침이 되면 되므로 재현이 매우 어렵다.
 */
class ChildBirthDateBoundaryTest {

	@Test
	@DisplayName("한국 시각으로 오늘이면 UTC 로 아직 어제여도 등록된다")
	void todayInKoreaIsNotFutureDate() {
		// 2026-09-09T16:00Z 는 한국 시각으로 2026-09-10 새벽 1시다.
		Clock koreanMidnight = Clock.fixed(
			Instant.parse("2026-09-09T16:00:00Z"), ZoneOffset.UTC);

		ChildRepository childRepository = mock(ChildRepository.class);
		DeviceRepository deviceRepository = mock(DeviceRepository.class);
		UserRepository userRepository = mock(UserRepository.class);

		when(childRepository.countByUserIdAndDeletedAtIsNull(anyLong())).thenReturn(0);
		when(childRepository.save(any(Child.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		ChildService childService = new ChildService(
			childRepository, deviceRepository, userRepository, koreanMidnight);

		ChildRequest request = new ChildRequest(
			"김아이", LocalDate.parse("2026-09-10"), "PARENT");

		assertThatCode(() -> childService.register(1L, request)).doesNotThrowAnyException();
	}

}
