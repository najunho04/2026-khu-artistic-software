package artistic.software.khu.artistic_software_khu.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import artistic.software.khu.artistic_software_khu.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

/**
 * "ERD.md" 3장 테이블 목록과 4장 제약조건·인덱스가 실제 DB 에 그대로 반영됐는지 확인한다.
 *
 * 실제 PostgreSQL 컨테이너에 마이그레이션을 돌린 뒤 검증하는 이유는, 여기서 확인하려는
 * 것들이 대부분 "부분 유니크 인덱스" 이기 때문이다. where 조건이 붙은 유니크 인덱스는
 * PostgreSQL 고유 기능이라 임베디드 DB 로는 재현되지 않는다. 스키마 파일을 눈으로 읽어
 * 확인하는 것도 의미가 없다. 조건이 실제로 걸렸는지는 데이터를 넣어봐야 안다.
 *
 * 이 테스트가 특히 중요한 이유는 부분 유니크 인덱스의 where 절이 빠져도 평소에는
 * 아무 문제가 없다는 데 있다. 기기 재페어링이나 루틴 재생성처럼 드문 흐름에서만
 * 터지고, 그때는 이미 운영 중이다.
 */
@EnabledIfDockerAvailable
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationIntegrationTest {

	// "ERD.md" 3장 전체 테이블 요약의 11개.
	// 커뮤니티 3개(POSTS / COMMENTS / POST_LIKES)는 API 를 구현하지 않지만
	// "정의만 유지" 하기로 했으므로 테이블은 만든다. 나중에 운영 중인 DB 에
	// 테이블을 추가하는 것이 지금 만들어두는 것보다 훨씬 비싸기 때문이다.
	private static final List<String> EXPECTED_TABLE_NAMES = List.of(
		"users",
		"children",
		"devices",
		"routine_templates",
		"big_routines",
		"small_routines",
		"characters",
		"child_characters",
		"posts",
		"comments",
		"post_likes"
	);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("ERD 11개 테이블이 모두 존재한다")
	void allTablesDefinedInErdExist() {
		List<String> actualTableNames = jdbcTemplate.queryForList(
			"select table_name from information_schema.tables where table_schema = 'public'",
			String.class);

		assertThat(actualTableNames).containsAll(EXPECTED_TABLE_NAMES);
	}

	@Test
	@DisplayName("같은 provider 와 provider_user_id 로는 두 번 가입할 수 없다")
	void socialAccountCannotSignUpTwice() {
		insertUser("GOOGLE", "google-uid-0001");

		assertThatThrownBy(() -> insertUser("GOOGLE", "google-uid-0001"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("기기를 해제한 뒤에는 같은 device_uid 로 다시 페어링할 수 있다")
	void deviceCanBePairedAgainAfterRelease() {
		long childId = insertChildOfNewUser("device-uid-user");

		Long firstDeviceId = insertDevice(childId, "device-uid-0001", null);

		// 살아 있는 행이 있는 동안에는 같은 기기를 또 등록할 수 없다
		assertThatThrownBy(() -> insertDevice(childId, "device-uid-0001", null))
			.isInstanceOf(DataIntegrityViolationException.class);

		// 연결을 해제하면(soft delete) 같은 기기를 다시 등록할 수 있어야 한다.
		// 이것이 unique(device_uid) 에 "where deleted_at is null" 이 붙는 이유다.
		// 조건이 빠지면 한 번 해제한 기기는 영원히 다시 연결할 수 없게 된다.
		jdbcTemplate.update("update devices set deleted_at = now() where id = ?", firstDeviceId);

		assertThatCode(() -> insertDevice(childId, "device-uid-0001", null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("PENDING 상태인 같은 페어링 코드가 두 개일 수 없다")
	void pendingPairingCodeIsUnique() {
		long childId = insertChildOfNewUser("pairing-code-user");

		insertDevice(childId, "device-uid-1001", "482913");

		// claim 이 코드 하나로 행 하나를 특정할 수 있어야 하므로 중복이 있으면 안 된다
		assertThatThrownBy(() -> insertDevice(childId, "device-uid-1002", "482913"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("자녀 한 명이 기기를 여러 대 가질 수 있다")
	void childCanOwnMultipleDevices() {
		long childId = insertChildOfNewUser("multi-device-user");

		insertDevice(childId, "device-uid-2001", null);

		// 기획이 1:1 에서 1:N 으로 바뀌었다. child_id 에 단독 유니크 제약이
		// 남아 있으면 두 번째 기기 등록이 막힌다.
		assertThatCode(() -> insertDevice(childId, "device-uid-2002", null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("같은 템플릿에서 같은 날짜의 빅루틴이 두 번 생성될 수 없다")
	void lazyCreatedBigRoutineIsUniquePerTemplateAndDate() {
		long childId = insertChildOfNewUser("lazy-creation-user");
		long templateId = insertRoutineTemplate(childId);

		insertBigRoutine(childId, templateId, "2026-09-01");

		// 지연 생성 트리거가 세 곳(routines / calendar / sync)이라 동시에 호출되면
		// 같은 날짜 루틴이 중복 생성될 수 있다. DB 가 마지막 방어선이다.
		assertThatThrownBy(() -> insertBigRoutine(childId, templateId, "2026-09-01"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertUser(String provider, String providerUserId) {
		jdbcTemplate.update(
			"insert into users (provider, provider_user_id) values (?, ?)",
			provider, providerUserId);
	}

	/**
	 * 자녀 한 명을 만들고 그 id 를 돌려준다. 자녀는 보호자에 매달려 있으므로
	 * 보호자부터 만든다. providerUserId 를 테스트마다 다르게 받는 이유는
	 * 소셜 계정 유니크 제약에 걸리지 않게 하기 위해서다.
	 */
	private long insertChildOfNewUser(String providerUserId) {
		Long userId = jdbcTemplate.queryForObject(
			"insert into users (provider, provider_user_id) values (?, ?) returning id",
			Long.class, "GOOGLE", providerUserId);

		return jdbcTemplate.queryForObject(
			"insert into children (user_id, name) values (?, ?) returning id",
			Long.class, userId, "아이");
	}

	private Long insertDevice(long childId, String deviceUid, String pairingCode) {
		// pairingCode 가 있으면 아직 claim 전이므로 PENDING, 없으면 연결이 끝난 ACTIVE 로 둔다
		String status = (pairingCode == null) ? "ACTIVE" : "PENDING";
		return jdbcTemplate.queryForObject(
			"insert into devices (child_id, device_uid, pairing_code, status)"
				+ " values (?, ?, ?, ?) returning id",
			Long.class, childId, deviceUid, pairingCode, status);
	}

	private long insertRoutineTemplate(long childId) {
		return jdbcTemplate.queryForObject(
			"insert into routine_templates (child_id, title) values (?, ?) returning id",
			Long.class, childId, "아침 루틴");
	}

	private void insertBigRoutine(long childId, long templateId, String routineDate) {
		jdbcTemplate.update(
			"insert into big_routines (child_id, template_id, routine_date, title)"
				+ " values (?, ?, cast(? as date), ?)",
			childId, templateId, routineDate, "아침 루틴");
	}

}
