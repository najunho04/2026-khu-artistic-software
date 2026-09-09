package artistic.software.khu.artistic_software_khu.child;

/**
 * 보호자와 자녀의 관계. "API.md" 6장에서 2026-09-04 에 확정했다.
 *
 * 값이 두 개뿐인 것은 "임시 확정" 이다. 타겟이 일반학교 특수학급으로 확장되면
 * 교사 관련 값이 필요해질 수 있다("ERD.md" 7장). 그때는 여기에 값을 더하면 되고
 * 이미 저장된 데이터는 그대로 두어도 된다.
 *
 * DB 에는 "check" 제약을 걸지 않는다. 값이 늘 때마다 마이그레이션으로 제약을
 * 고치는 비용이 얻는 것보다 크기 때문이다. 값 검증은 이 열거형이 한다.
 * 그래서 이 파일이 "어떤 값이 허용되는가" 에 대한 유일한 출처다.
 */
public enum Relationship {

	PARENT,
	ADMIN

}
