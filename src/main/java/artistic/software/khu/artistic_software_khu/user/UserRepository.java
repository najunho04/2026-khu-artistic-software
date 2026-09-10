package artistic.software.khu.artistic_software_khu.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 보호자 계정 조회. 모든 조회에 "deletedAt is null" 조건이 붙는다.
 *
 * "ERD.md" 가 정한 soft delete 방식 때문이다. 조건을 빠뜨리면 탈퇴한 계정이
 * 그대로 로그인되거나 이미 쓴 이메일로 가입이 막히는 일이 생긴다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByAccessUuidAndDeletedAtIsNull(UUID accessUuid);

	boolean existsByEmailAndDeletedAtIsNull(String email);

	/**
	 * 보호자 행을 "잠그고" 읽는다. 자녀 수 상한 검사에 쓴다.
	 *
	 * 세는 것과 넣는 것 사이에 다른 요청이 끼어들면 둘 다 상한을 통과해
	 * 11명이 등록된다. 잠금(다른 트랜잭션을 앞의 것이 끝날 때까지 기다리게
	 * 하는 장치)이 그 틈을 없앤다. 보호자 한 명에 걸린 요청만 기다린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user"
		+ " where user.id = :userId and user.deletedAt is null")
	Optional<User> findByIdAndDeletedAtIsNullForUpdate(@Param("userId") Long userId);

}
