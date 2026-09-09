package artistic.software.khu.artistic_software_khu.child;

import java.time.LocalDate;

/**
 * 자녀 단건 응답. "API.md" 6장.
 */
public record ChildResponse(
	Long childId,
	String name,
	LocalDate birthDate,
	Relationship relationship) {

	public static ChildResponse from(Child child) {
		return new ChildResponse(
			child.getId(), child.getName(), child.getBirthDate(), child.getRelationship());
	}

}
