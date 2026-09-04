package artistic.software.khu.artistic_software_khu.config;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;

/**
 * "API.md" 1-3 이 정한 날짜·시간 포맷을 JSON 직렬화에 적용하는 모듈.
 *
 * date(yyyy-MM-dd)와 timestamp(ISO-8601 UTC)는 Jackson 의 기본 동작이 이미
 * 문서와 같아서 따로 손대지 않는다. 여기서 바꾸는 것은 time 하나다.
 *
 * time 을 굳이 고정하는 이유는 Jackson 의 기본 LocalTime 직렬화가 초 값에 따라
 * 결과 길이를 바꾸기 때문이다. 초가 0 이면 "08:30" 이지만 초가 있으면
 * "08:30:15" 가 되어 같은 필드가 상황에 따라 다른 모양으로 나간다. 기기 펌웨어가
 * 고정 길이로 파싱한다면 이것만으로 깨지는데, 서버에서는 아무 오류도 나지 않아
 * 원인을 찾기 어렵다. 그래서 항상 "HH:mm" 으로 못박는다.
 *
 * time 에 시간대를 붙이지 않는 것도 의도한 것이다. "ERD.md" 5-2 가 time 컬럼을
 * "KST 벽시계 시각" 으로 해석하기로 정했기 때문이다. 아침 8시 30분에 일어난다는
 * 약속은 시간대를 옮겨도 여전히 아침 8시 30분이어야 한다.
 */
public class JsonTimeFormatModule extends SimpleModule {

	// "API.md" 1-3 의 time 포맷. 초와 밀리초는 쓰지 않는다.
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	public JsonTimeFormatModule() {
		super("JsonTimeFormatModule");

		addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
		addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_FORMATTER));
	}

}
