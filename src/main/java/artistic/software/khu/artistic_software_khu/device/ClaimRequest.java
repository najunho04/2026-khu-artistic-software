package artistic.software.khu.artistic_software_khu.device;

/**
 * 기기 등록 요청. "API.md" 8장 POST /device-api/v1/claim.
 *
 * **인증 헤더가 없는 요청이다.** 이 시점의 기기는 아직 아무 신분증도 없고,
 * 신분증을 받으려고 이 호출을 하는 것이기 때문이다.
 */
public record ClaimRequest(String pairingCode, String deviceUid, String firmware) {
}
