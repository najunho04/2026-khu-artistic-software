package artistic.software.khu.artistic_software_khu;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

/**
 * 테스트용으로 뜨는 PostgreSQL 컨테이너가 "정말로" 문서에 적힌 메이저 버전인지 확인한다.
 *
 * 이 테스트가 필요한 이유는 이미지 태그를 "latest" 로 두면 어느 날 업스트림이 다음
 * 메이저 버전을 latest 로 밀어버리는 순간, 아무도 코드를 건드리지 않았는데 테스트만
 * 조용히 다른 DB 버전에서 돌기 시작하기 때문이다. ERD.md 와 ROADMAP.md 는 DBMS 를
 * "PostgreSQL 17" 로 못박고 있으므로, 그 약속을 문서가 아니라 테스트로 고정한다.
 *
 * 태그 문자열을 검사하지 않고 실제로 뜬 DB 에 접속해 버전을 물어보는 이유는,
 * 태그와 실제 이미지가 어긋나는 경우까지 잡아내기 위해서다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PostgreSqlVersionIntegrationTest {

	// ERD.md 및 ROADMAP.md 기술 스택 항목에서 확정한 값
	private static final int EXPECTED_POSTGRESQL_MAJOR_VERSION = 17;

	@Autowired
	private DataSource dataSource;

	@Test
	void postgreSqlContainerRunsExpectedMajorVersion() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData databaseMetaData = connection.getMetaData();

			assertThat(databaseMetaData.getDatabaseProductName())
				.as("테스트용 DB 는 임베디드 DB 가 아니라 실제 PostgreSQL 이어야 한다 (T-2)")
				.isEqualTo("PostgreSQL");

			assertThat(databaseMetaData.getDatabaseMajorVersion())
				.as("Testcontainers 이미지 태그가 문서상 PostgreSQL %d 에 고정되어 있지 않다. "
					+ "TestcontainersConfiguration 의 이미지 태그를 확인하라.",
					EXPECTED_POSTGRESQL_MAJOR_VERSION)
				.isEqualTo(EXPECTED_POSTGRESQL_MAJOR_VERSION);
		}
	}

}
