package artistic.software.khu.artistic_software_khu.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
