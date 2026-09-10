package artistic.software.khu.artistic_software_khu.character;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자녀 보유 캐릭터 조회.
 *
 * soft delete 컬럼이 없다("ERD.md" 3장). 자녀가 지워지면 이 행을 읽는 경로가
 * 함께 막히므로 따로 지우지 않는다.
 */
public interface ChildCharacterRepository extends JpaRepository<ChildCharacter, Long> {

	List<ChildCharacter> findAllByChildIdOrderByAcquiredAtAscIdAsc(Long childId);

	/**
	 * 지금 키우는 캐릭터 한 마리를 찾는다. 아직 다 자라지 않은 마리다.
	 *
	 * 경험치를 여러 마리에 나눠 넣지 않기 때문에 이런 마리는 자녀당 최대
	 * 한 마리뿐이다. 그래도 "First" 로 뽑는 이유는, 데이터가 어떤 이유로든
	 * 둘이 되었을 때 먼저 받은 쪽부터 채우는 것이 자연스럽기 때문이다.
	 */
	Optional<ChildCharacter> findFirstByChildIdAndExpLessThanOrderByAcquiredAtAscIdAsc(
		Long childId, int exp);

}
