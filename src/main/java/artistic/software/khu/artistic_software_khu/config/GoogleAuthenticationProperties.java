package artistic.software.khu.artistic_software_khu.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 소셜 로그인 검증에 필요한 설정값.
 *
 * client ID 를 코드에 박지 않고 설정으로 빼는 이유는 값이 환경마다 다르기
 * 때문이다. 개발용 앱과 배포용 앱이 서로 다른 client ID 를 쓴다.
 *
 * client ID 자체는 비밀값이 아니다. 앱에 그대로 박혀 배포되는 공개 정보다.
 * 그래도 설정으로 빼두면 환경별로 갈아끼우기 쉽고, 저장소에 값을 넣을지
 * 환경변수로 줄지를 나중에 고를 수 있다.
 *
 * @param allowedClientIds 우리 앱의 구글 client ID 목록. iOS 와 안드로이드가
 *                         서로 다른 값을 쓰므로 보통 두 개가 들어간다.
 *                         비어 있으면 모든 idToken 검증이 거부된다.
 */
@ConfigurationProperties(prefix = "yeso.auth.google")
public record GoogleAuthenticationProperties(List<String> allowedClientIds) {

	public GoogleAuthenticationProperties {
		// 설정이 아예 없으면 null 이 들어온다. 빈 목록으로 바꿔 두면
		// 이후 코드가 null 을 신경 쓰지 않아도 된다.
		allowedClientIds = (allowedClientIds == null) ? List.of() : List.copyOf(allowedClientIds);
	}

}
