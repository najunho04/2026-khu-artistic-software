package artistic.software.khu.artistic_software_khu.auth;

/**
 * 소셜 idToken 검증에 성공했을 때 그 안에서 뽑아낸 계정 정보.
 *
 * 이 값들이 그대로 USERS 행이 된다. providerUserId 는 provider_user_id 로,
 * 나머지는 같은 이름의 컬럼으로 들어간다.
 *
 * @param providerUserId 제공자가 계정마다 부여한 고유 식별자. 구글에서는 sub 클레임이다.
 *                       이메일이 아니라 이 값을 식별자로 쓰는 이유는 이메일은 바뀔 수
 *                       있지만 이 값은 바뀌지 않기 때문이다.
 * @param email          계정 이메일. 요청한 권한 범위에 따라 비어 있을 수 있다.
 * @param name           계정 이름. 비어 있을 수 있다. 보호자 성명은 온보딩 1차에서
 *                       따로 입력받으므로 이 값에 의존하지 않는다.
 */
public record SocialAccount(String providerUserId, String email, String name) {
}
