package artistic.software.khu.artistic_software_khu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * Testcontainers 기반 통합 테스트가 조용히 건너뛰어진 채 빌드가 성공하는 것을 막는다.
 * Docker가 없으면 통합 테스트는 @EnabledIfDockerAvailable로 skip되므로,
 * 이 테스트가 대신 실패해서 "검증되지 않은 그린"을 드러낸다.
 */
class DockerAvailabilityTest {

	@Test
	void dockerIsAvailable() {
		assertTrue(
			DockerClientFactory.instance().isDockerAvailable(),
			"""
			Docker를 찾을 수 없어 Testcontainers 통합 테스트가 모두 건너뛰어졌습니다.
			Docker Desktop을 실행하고 WSL integration을 켠 뒤 다시 시도하세요.
			(통합 테스트 없이 빌드만 필요하면: ./gradlew build -x test)"""
		);
	}

}
