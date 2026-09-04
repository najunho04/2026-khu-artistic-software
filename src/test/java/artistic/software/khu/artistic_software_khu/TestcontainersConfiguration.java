package artistic.software.khu.artistic_software_khu;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// ERD.md 와 ROADMAP.md 가 DBMS 를 "PostgreSQL 17" 로 확정했기 때문에 메이저 버전을 태그에 고정한다.
	// "latest" 를 쓰면 업스트림이 다음 메이저 버전을 latest 로 올리는 순간 아무도 코드를 건드리지
	// 않았는데 테스트만 다른 DB 버전에서 돌게 된다. 실제로 이 프로젝트에서도 latest 가 이미 18 이었다.
	// 이 고정이 풀리면 PostgreSqlVersionIntegrationTest 가 실패해서 알려준다.
	private static final String POSTGRESQL_IMAGE_NAME = "postgres:17";

	// Redis 는 세 문서 어디에도 버전이 적혀 있지 않아 임의로 고정하지 않고 태그를 그대로 둔다.
	// 버전이 확정되면 위와 같은 방식으로 고정한다.
	private static final String REDIS_IMAGE_NAME = "redis:latest";

	private static final int REDIS_DEFAULT_PORT = 6379;

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRESQL_IMAGE_NAME));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE_NAME)).withExposedPorts(REDIS_DEFAULT_PORT);
	}

}
