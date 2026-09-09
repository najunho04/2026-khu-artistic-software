package artistic.software.khu.artistic_software_khu.device;

/**
 * 기기 정보 수정 요청. "API.md" 7장. 지금은 별칭만 바꾼다.
 */
public record DeviceUpdateRequest(String nickname) {
}
