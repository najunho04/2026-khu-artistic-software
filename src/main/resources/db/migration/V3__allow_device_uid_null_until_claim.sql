-- device_uid 에서 not null 을 푼다. Phase 2-2 페어링을 구현하면서 드러난 문제다.
--
-- 페어링은 두 단계로 나뉜다. 앱이 "POST /devices/pairing" 으로 코드만 들어 있는
-- PENDING 행을 먼저 만들고, 그 뒤에 기기가 "POST /device-api/v1/claim" 으로
-- 자기 device_uid 를 가져온다. "API.md" 7장이 "deviceUid 는 받지 않습니다.
-- 기기가 claim 시 직접 전달합니다" 라고 적은 그대로다.
--
-- 즉 PENDING 행이 만들어지는 시점에는 device_uid 를 알 수 없다. not null 이면
-- 페어링 요청 자체가 실패한다. 아무 값이나 넣어두는 방법도 있지만, 그러면
-- "아직 안 정해짐" 과 "실제 기기 값" 을 구분할 수 없게 되고 유니크 제약도
-- 그 가짜 값들끼리 충돌한다.
--
-- V1 에서 not null 이었던 것은 1기기 시절의 흔적으로 보인다. 그때는 기기 행이
-- claim 시점에 한 번에 만들어졌을 것이다.
alter table devices alter column device_uid drop not null;

-- unique(device_uid) where deleted_at is null 은 그대로 둔다.
-- PostgreSQL 의 유니크 인덱스는 NULL 을 서로 다른 값으로 보므로,
-- device_uid 가 비어 있는 PENDING 행이 여러 개 있어도 충돌하지 않는다.
