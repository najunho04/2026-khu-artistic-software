package artistic.software.khu.artistic_software_khu.config;

import java.util.Set;

/**
 * 로그에 남기면 안 되는 값을 가린다. "API.md" 15장에서 확정한 방식이다.
 *
 * 가리는 방식이 값에 따라 둘로 나뉘는 것이 이 클래스의 요점이다.
 *
 * 1. UUID 계열은 앞뒤 세 글자만 남긴다. 장애를 쫓을 때 "어느 요청들이 한 사람의
 *    것인가" 를 알아야 하기 때문이다. 가운데가 없으면 그 값으로 인증할 수는 없다.
 * 2. 비밀번호와 페어링 코드는 통째로 가린다. 비밀번호는 일부만 알려줘도 나머지를
 *    추측할 여지가 크게 늘어나고, 추적할 이유도 없다. 페어링 코드는 여섯 자리라
 *    앞뒤 세 글자를 남기면 값이 전부 드러난다.
 *
 * 한 가지 방식으로 통일하지 않은 이유가 여기 있다. 전부 가리면 장애를 쫓을 수 없고,
 * 전부 일부만 남기면 짧은 값이 사실상 그대로 노출된다.
 *
 * 특히 "access_uuid" 는 만료가 없다. 통째로 로그에 남으면 그 값을 본 사람이
 * 계속 그 계정으로 행세할 수 있고, 로그아웃 전까지 막을 방법이 없다.
 */
public class SensitiveValueMasker {

	// 통째로 가릴 필드. 소문자로 비교하므로 여기에도 소문자로 적는다.
	private static final Set<String> FULLY_MASKED_FIELD_NAMES = Set.of(
		"password",
		"passwordhash",
		"password_hash",
		"pairingcode",
		"pairing_code");

	// 앞뒤만 남길 필드. API 필드 이름(camelCase)과 DB 컬럼 이름(snake_case)이
	// 둘 다 로그에 나올 수 있어 양쪽을 모두 적는다. ("API.md" 1-2)
	private static final Set<String> PARTIALLY_MASKED_FIELD_NAMES = Set.of(
		"accessuuid",
		"access_uuid",
		"deviceaccessuuid",
		"device_access_uuid",
		// 헤더 이름으로도 들어온다. ("API.md" 1-1)
		"x-access-uuid",
		"x-device-uuid");

	private static final String FULL_MASK = "***";

	private static final String PARTIAL_MASK_SEPARATOR = "...";

	// 앞뒤로 남길 글자 수.
	private static final int VISIBLE_EDGE_LENGTH = 3;

	// 부분 가리기가 의미를 가지려면 가운데에 최소 한 글자는 가려져야 한다.
	// 그래서 앞 3 + 뒤 3 보다 "긴" 값에만 적용한다. 여섯 글자 이하는
	// 앞뒤를 남기는 순간 값이 전부 드러나므로 통째로 가린다.
	private static final int MINIMUM_LENGTH_FOR_PARTIAL_MASK = VISIBLE_EDGE_LENGTH * 2 + 1;

	/**
	 * 필드 이름에 따라 값을 가린다.
	 *
	 * @param fieldName 필드 · 헤더 이름. 대소문자는 구분하지 않는다
	 * @param value     원래 값
	 * @return 가려진 값. 가릴 대상이 아니면 원래 값을 그대로 돌려준다
	 */
	public String mask(String fieldName, String value) {
		// 없는 값을 "***" 로 바꾸면 로그를 읽는 사람이 "무언가 들어 있었는데
		// 가려졌다" 고 오해한다. 실제로는 아무것도 오지 않은 것이다.
		if (value == null || value.isEmpty()) {
			return value;
		}

		if (fieldName == null) {
			return value;
		}

		String normalizedFieldName = fieldName.toLowerCase();

		if (FULLY_MASKED_FIELD_NAMES.contains(normalizedFieldName)) {
			return FULL_MASK;
		}

		if (PARTIALLY_MASKED_FIELD_NAMES.contains(normalizedFieldName)) {
			return maskKeepingBothEnds(value);
		}

		return value;
	}

	/**
	 * 앞뒤 세 글자만 남기고 가운데를 가린다.
	 *
	 * 값이 짧아 가릴 것이 남지 않으면 부분 가리기를 포기하고 통째로 가린다.
	 * 가리는 시늉만 하고 실제로는 다 보여주는 것이 가장 나쁘다.
	 * 아무도 가려졌다고 믿고 다시 확인하지 않기 때문이다.
	 */
	private String maskKeepingBothEnds(String value) {
		if (value.length() < MINIMUM_LENGTH_FOR_PARTIAL_MASK) {
			return FULL_MASK;
		}

		String head = value.substring(0, VISIBLE_EDGE_LENGTH);
		String tail = value.substring(value.length() - VISIBLE_EDGE_LENGTH);

		return head + PARTIAL_MASK_SEPARATOR + tail;
	}

}
