package artistic.software.khu.artistic_software_khu.device;

/**
 * 페어링 코드 발급 요청. "API.md" 7장.
 *
 * deviceUid 를 받지 않는 것이 핵심이다. 이 시점에는 어떤 기기가 붙을지 모른다.
 * 기기가 claim 할 때 스스로 가져온다.
 */
public record PairingRequest(Long childId, String nickname) {
}
