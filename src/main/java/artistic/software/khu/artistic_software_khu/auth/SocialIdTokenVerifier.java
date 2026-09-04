package artistic.software.khu.artistic_software_khu.auth;

/**
 * 소셜 제공자가 발급한 idToken 이 진짜인지 확인하고 계정 정보를 뽑아내는 역할.
 *
 * 구현체를 갈아끼울 수 있게 인터페이스로 두는 것이 핵심이다. 실제 구현체는
 * 구글 공개키로 서명을 검증하지만, 단위 테스트와 CI 에서는 정해진 값을 돌려주는
 * 가짜 구현체를 끼운다. CI 가 구글에 네트워크 요청을 보내면 구글이 느리거나
 * 죽었을 때 우리 잘못이 아닌 이유로 빌드가 실패하고, 테스트용 계정과 비밀키를
 * CI 에 넣어야 하는 문제도 생긴다.
 *
 * 실제 구현체를 쓰는 통합 실검증은 로컬에서 사람이 수동으로 돌린다. (T-3)
 */
public interface SocialIdTokenVerifier {

	/**
	 * 이 구현체가 담당하는 제공자.
	 *
	 * 제공자가 늘어나면 구현체를 하나 더 만들고, 호출하는 쪽이 이 값으로
	 * 알맞은 구현체를 고르게 된다.
	 */
	SocialProvider getSupportedProvider();

	/**
	 * idToken 을 검증하고 계정 정보를 돌려준다.
	 *
	 * 검증에 실패하면 AUTH_INVALID_ID_TOKEN 을 담은 BusinessException 을 던진다.
	 * 서명이 틀렸는지 만료됐는지 다른 앱 것인지를 구분해서 알려주지 않는데,
	 * 이는 앱 입장에서 할 수 있는 일이 "다시 로그인" 하나로 같기 때문이며,
	 * 실패 사유를 밖으로 흘리지 않기 위해서이기도 하다.
	 */
	SocialAccount verify(String idToken);

}
