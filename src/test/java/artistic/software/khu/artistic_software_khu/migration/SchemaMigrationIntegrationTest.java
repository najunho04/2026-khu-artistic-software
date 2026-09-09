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
	@DisplayName("같은 이메일로는 두 번 가입할 수 없다")
	void sameEmailCannotSignUpTwice() {
		insertUser("parent@example.com");

		// 이메일이 로그인 식별자다. 중복을 허용하면 로그인할 때 어느 계정인지
		// 특정할 수 없게 된다.
		assertThatThrownBy(() -> insertUser("parent@example.com"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("탈퇴한 뒤에는 같은 이메일로 다시 가입할 수 있다")
	void emailCanBeReusedAfterWithdrawal() {
		Long userId = jdbcTemplate.queryForObject(
			"insert into users (email, password_hash) values (?, ?) returning id",
			Long.class, "rejoin@example.com", "hashed");

		// unique(email) 에 "where deleted_at is null" 이 붙는 이유다.
		// 조건이 빠지면 한 번 탈퇴한 사람은 같은 이메일로 영영 다시 가입할 수 없다.
		jdbcTemplate.update("update users set deleted_at = now() where id = ?", userId);

		assertThatCode(() -> insertUser("rejoin@example.com"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("이메일 없이는 가입할 수 없다")
	void emailIsRequired() {
		// 로그인 식별자가 비어 있으면 그 계정으로는 로그인할 방법이 없다.
		assertThatThrownBy(() -> jdbcTemplate.update(
			"insert into users (password_hash) values (?)", "hashed"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("access_uuid 는 같은 값이 두 개일 수 없다")
	void accessUuidIsUnique() {
		String sharedUuid = "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d";
		insertUserWithAccessUuid("first@example.com", sharedUuid);

		// 이 값 하나로 유저를 특정하므로 겹치면 두 사람이 서로의 계정으로 들어간다.
		assertThatThrownBy(() -> insertUserWithAccessUuid("second@example.com", sharedUuid))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("소셜 로그인 컬럼은 비어 있어도 가입할 수 있다")
	void socialColumnsAreOptional() {
		// provider 와 provider_user_id 는 지우지 않고 nullable 로 남겼다.
		// 나중에 소셜 로그인을 붙일 때 쌓인 데이터를 옮기지 않아도 되게 하기 위해서다.
		// PostgreSQL 의 유니크 제약은 NULL 을 서로 다른 값으로 보므로
		// 두 컬럼이 모두 비어 있는 행이 여러 개 있어도 충돌하지 않는다.
		insertUser("nosocial1@example.com");

		assertThatCode(() -> insertUser("nosocial2@example.com"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("기기를 해제한 뒤에는 같은 device_uid 로 다시 페어링할 수 있다")
	void deviceCanBePairedAgainAfterRelease() {
		long childId = insertChildOfNewUser("device-uid@example.com");

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
		long childId = insertChildOfNewUser("pairing-code@example.com");

		insertDevice(childId, "device-uid-1001", "482913");

		// claim 이 코드 하나로 행 하나를 특정할 수 있어야 하므로 중복이 있으면 안 된다
		assertThatThrownBy(() -> insertDevice(childId, "device-uid-1002", "482913"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("자녀 한 명이 기기를 여러 대 가질 수 있다")
	void childCanOwnMultipleDevices() {
		long childId = insertChildOfNewUser("multi-device@example.com");

		insertDevice(childId, "device-uid-2001", null);

		// 기획이 1:1 에서 1:N 으로 바뀌었다. child_id 에 단독 유니크 제약이
		// 남아 있으면 두 번째 기기 등록이 막힌다.
		assertThatCode(() -> insertDevice(childId, "device-uid-2002", null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("같은 날짜에 같은 제목의 빅루틴을 여러 개 만들 수 있다")
	void sameChildCanHaveMultipleBigRoutinesOnOneDate() {
		long childId = insertChildOfNewUser("multi-routine@example.com");

		insertBigRoutine(childId, "2026-09-01");

		// 예전에는 unique(template_id, routine_date) 가 걸려 있었다. 지연 생성이
		// 세 곳에서 동시에 일어날 수 있어 DB 가 마지막 방어선이어야 했기 때문이다.
		// 즉시 생성으로 바뀌면서 동시에 같은 행을 만들 자리가 없어졌고, 제약도 뺐다.
		// 제약이 남아 있으면 "아침 준비" 와 "저녁 준비" 를 같은 날에 만들 수 없다.
		assertThatCode(() -> insertBigRoutine(childId, "2026-09-01"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("페어링 중인 기기는 device_uid 없이 저장할 수 있다")
	void deviceUidIsNullUntilClaim() {
		long childId = insertChildOfNewUser("pending-device@example.com");

		// 페어링은 두 단계다. 앱이 코드만 들어 있는 PENDING 행을 먼저 만들고,
		// 기기가 claim 할 때 device_uid 를 가져온다. not null 이면 첫 단계에서
		// 막혀 페어링 자체가 불가능해진다.
		assertThatCode(() -> jdbcTemplate.update(
			"insert into devices (child_id, pairing_code, status) values (?, ?, 'PENDING')",
			childId, "0123456789"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("device_uid 가 비어 있는 기기가 여러 대 있어도 충돌하지 않는다")
	void multiplePendingDevicesWithoutUidDoNotCollide() {
		long childId = insertChildOfNewUser("many-pending@example.com");

		jdbcTemplate.update(
			"insert into devices (child_id, pairing_code, status) values (?, ?, 'PENDING')",
			childId, "1111111111");

		// 자녀당 기기가 여러 대라 PENDING 행이 동시에 여럿일 수 있다.
		// PostgreSQL 의 유니크 인덱스는 NULL 을 서로 다른 값으로 보므로
		// device_uid 가 비어 있는 행끼리는 충돌하지 않는다.
		assertThatCode(() -> jdbcTemplate.update(
			"insert into devices (child_id, pairing_code, status) values (?, ?, 'PENDING')",
			childId, "2222222222"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("삭제된 컬럼들이 실제로 사라졌다")
	void removedColumnsAreGone() {
		// 문서에서만 지우고 DB 에 남겨두면 엔티티를 만들 때 ddl-auto=validate 가
		// 통과해버려 어긋난 것을 모르고 지나간다.
		assertThat(columnNamesOf("users")).doesNotContain("access_token");
		assertThat(columnNamesOf("devices")).doesNotContain("token_hash", "secret_hash");
		assertThat(columnNamesOf("routine_templates")).doesNotContain("is_active");
		assertThat(columnNamesOf("big_routines")).doesNotContain("template_id");
	}

	@Test
	@DisplayName("추가된 컬럼들이 실제로 생겼다")
	void addedColumnsExist() {
		assertThat(columnNamesOf("users")).contains("password_hash", "access_uuid");
		assertThat(columnNamesOf("devices")).contains("device_access_uuid");
	}

	@Test
	@DisplayName("device_access_uuid 는 같은 값이 두 개일 수 없다")
	void deviceAccessUuidIsUnique() {
		long childId = insertChildOfNewUser("device-uuid@example.com");
		String sharedUuid = "7c9e4d21-8b3a-4f60-a1d5-2e8c0b7f3a94";

		jdbcTemplate.update(
			"insert into devices (child_id, device_uid, status, device_access_uuid)"
				+ " values (?, ?, ?, cast(? as uuid))",
			childId, "device-uid-3001", "ACTIVE", sharedUuid);

		// 기기 인증의 식별자다. 겹치면 두 기기가 서로의 자녀 루틴을 받아 간다.
		assertThatThrownBy(() -> jdbcTemplate.update(
			"insert into devices (child_id, device_uid, status, device_access_uuid)"
				+ " values (?, ?, ?, cast(? as uuid))",
			childId, "device-uid-3002", "ACTIVE", sharedUuid))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * 컬럼 이름 목록을 돌려준다. 컬럼이 사라졌는지 생겼는지를 확인할 때 쓴다.
	 */
	private List<String> columnNamesOf(String tableName) {
		return jdbcTemplate.queryForList(
			"select column_name from information_schema.columns"
				+ " where table_schema = 'public' and table_name = ?",
			String.class, tableName);
	}

	private void insertUser(String email) {
		jdbcTemplate.update(
			"insert into users (email, password_hash) values (?, ?)",
			email, "hashed-password-value");
	}

	private void insertUserWithAccessUuid(String email, String accessUuid) {
		jdbcTemplate.update(
			"insert into users (email, password_hash, access_uuid)"
				+ " values (?, ?, cast(? as uuid))",
			email, "hashed-password-value", accessUuid);
	}

	/**
	 * 자녀 한 명을 만들고 그 id 를 돌려준다. 자녀는 보호자에 매달려 있으므로
	 * 보호자부터 만든다. email 을 테스트마다 다르게 받는 이유는
	 * 이메일 유니크 제약에 걸리지 않게 하기 위해서다.
	 */
	private long insertChildOfNewUser(String email) {
		Long userId = jdbcTemplate.queryForObject(
			"insert into users (email, password_hash) values (?, ?) returning id",
			Long.class, email, "hashed-password-value");

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

	private void insertBigRoutine(long childId, String routineDate) {
		jdbcTemplate.update(
			"insert into big_routines (child_id, routine_date, title)"
				+ " values (?, cast(? as date), ?)",
			childId, routineDate, "아침 루틴");
	}

}
