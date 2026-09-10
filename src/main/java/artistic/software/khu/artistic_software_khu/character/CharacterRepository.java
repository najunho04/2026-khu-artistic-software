package artistic.software.khu.artistic_software_khu.character;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 도감 조회. 모든 조회에 "isActive = true" 조건이 붙는다.
 *
 * 감춘 캐릭터가 목록에 섞이면 아직 공개하지 않은 캐릭터가 앱에 뜨고,
 * 지급 대상에 섞이면 아이가 그 캐릭터를 받아 버린다.
 */
public interface CharacterRepository extends JpaRepository<Character, Long> {

	List<Character> findAllByIsActiveTrueOrderByCodeAsc();

	/** 도감 순서에서 가장 앞선 캐릭터를 찾는다. 아직 한 마리도 없을 때 쓴다. */
	Optional<Character> findFirstByIsActiveTrueOrderByCodeAsc();

	/**
	 * 이미 가진 것을 뺀 나머지 중 도감 순서에서 가장 앞선 캐릭터를 찾는다.
	 *
	 * 가진 것이 하나도 없을 때는 이 메서드를 부르지 않는다. JPQL 의 "not in"
	 * 에 빈 목록을 넘기면 DB 에 따라 아무것도 찾지 못하거나 문법 오류가 난다.
	 */
	Optional<Character> findFirstByIsActiveTrueAndIdNotInOrderByCodeAsc(
		Collection<Long> excludedCharacterIds);

}
