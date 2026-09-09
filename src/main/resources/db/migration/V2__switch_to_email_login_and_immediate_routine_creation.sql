-- 2026-09-09 결정 두 가지를 스키마에 반영한다. "ERD.md" 최종 수정분과 짝을 이룬다.
--
-- 1. 로그인을 소셜(구글)에서 이메일 · 비밀번호로 바꾸고 JWT 를 없앤다.
-- 2. 루틴 반복 생성을 "조회 시점 지연 생성" 에서 "요청 시점 즉시 생성" 으로 바꾼다.
--
-- V1 을 고치지 않고 V2 를 따로 두는 이유는 Flyway 가 이미 적용한 파일의 내용이
-- 바뀌면 체크섬 불일치로 기동을 거부하기 때문이다. 적용된 마이그레이션은
-- 손대지 않고 다음 번호로 쌓는 것이 원칙이다.

-- ---------------------------------------------------------------------------
-- 1. 이메일 · 비밀번호 로그인 (USERS)
-- ---------------------------------------------------------------------------

-- 비밀번호를 되돌릴 수 없는 형태로 바꿔 저장할 자리. 평문은 어디에도 남기지 않는다.
alter table users add column password_hash varchar;

-- 로그인에 성공하면 서버가 발급하는 임의의 값. 앱이 이후 요청마다
-- "X-Access-Uuid" 헤더에 담아 보내고, 서버는 이 값으로 유저를 찾는다.
-- JWT 를 쓰지 않기로 해서 서명 검증도 만료 판정도 없다.
--
-- 로그인 전이거나 로그아웃한 상태에서는 비어 있으므로 nullable 이다.
alter table users add column access_uuid uuid;

-- provider 와 provider_user_id 에서 not null 을 푼다.
--
-- 컬럼 자체를 지우지 않는 이유는, 나중에 소셜 로그인을 다시 붙일 때 이미 쌓인
-- 데이터를 옮기지 않아도 되게 하기 위해서다. 지우는 것은 되돌리기 어렵고
-- nullable 로 두는 비용은 사실상 없다. ("ERD.md" 설계 노트)
alter table users alter column provider drop not null;
alter table users alter column provider_user_id drop not null;

-- users_provider_and_provider_user_id_unique 는 그대로 둔다.
-- PostgreSQL 의 유니크 제약은 NULL 을 서로 다른 값으로 보므로,
-- 두 컬럼이 모두 비어 있는 행이 여러 개 있어도 충돌하지 않는다.

-- 이메일이 로그인 식별자가 되므로 비어 있으면 안 된다.
--
-- 기존 행이 있으면 not null 을 걸 때 실패한다. V1 적용 직후라 데이터가 없지만,
-- 혹시 남아 있다면 먼저 값을 채워야 한다는 것을 여기 적어둔다.
alter table users alter column email set not null;

-- 이메일 중복을 막는다. 중복을 허용하면 로그인할 때 어느 계정인지 특정할 수 없다.
--
-- "where deleted_at is null" 을 붙이는 이유는 기기의 device_uid 와 같다.
-- 조건이 빠지면 한 번 탈퇴한 사람은 같은 이메일로 영영 다시 가입할 수 없다.
create unique index users_email_unique_when_not_deleted
    on users (email)
    where deleted_at is null;

-- 이 값 하나로 유저를 특정하므로 겹치면 두 사람이 서로의 계정으로 들어간다.
-- 동시에 모든 인증 요청이 이 컬럼으로 조회하므로 가장 자주 타는 인덱스이기도 하다.
-- 유니크 인덱스가 조회 인덱스 역할을 겸하므로 따로 index 를 만들지 않는다.
create unique index users_access_uuid_unique on users (access_uuid);

-- ---------------------------------------------------------------------------
-- 2. 기기 토큰 체계 제거 (DEVICES)
-- ---------------------------------------------------------------------------

-- 페어링이 완료되는 순간 서버가 발급해 기기에 내려주는 값.
-- 기기는 이후 요청마다 "X-Device-Uuid" 헤더에 담는다.
--
-- device_uid 를 그대로 신분증으로 쓰지 않는 이유는 그것이 기기가 만든 값이라
-- 다른 값을 추측하거나 지어낼 여지가 있기 때문이다. 서버가 만든 임의의 값이어야
-- 그 여지가 없다. ("ERD.md" 설계 노트)
--
-- 페어링 전(PENDING)에는 아직 발급되지 않았으므로 nullable 이다.
alter table devices add column device_access_uuid uuid;

-- 기기 인증의 식별자다. 겹치면 두 기기가 서로의 자녀 루틴을 받아 간다.
-- 해제한 기기의 행이 남아 있어도 새 기기가 값을 받을 수 있어야 하므로
-- device_uid 와 같은 조건을 붙인다.
create unique index devices_device_access_uuid_unique_when_not_deleted
    on devices (device_access_uuid)
    where deleted_at is null;

-- 기기용 토큰 체계를 없애면서 저장할 대상 자체가 사라졌다.
-- "/device-api/v1/token/refresh" 도 함께 삭제되어 이 두 컬럼을 읽을 곳이 없다.
alter table devices drop column token_hash;
alter table devices drop column secret_hash;

-- ---------------------------------------------------------------------------
-- 3. 루틴 즉시 생성 (ROUTINE_TEMPLATES / BIG_ROUTINES)
-- ---------------------------------------------------------------------------

-- 지연 생성 동시성 방어용 제약이었다.
--
-- 조회 지점 세 곳(routines / calendar / sync)이 동시에 호출되면 같은 날짜 루틴이
-- 중복 생성될 수 있어 DB 가 마지막 방어선이어야 했다. 즉시 생성으로 바뀌면서
-- 동시에 같은 행을 만들 자리 자체가 없어졌다.
--
-- 그냥 불필요해진 것이 아니라 "남아 있으면 안 되는" 제약이다. 이 제약이 있으면
-- 같은 날에 빅루틴을 두 개 만들 수 없다. template_id 가 둘 다 NULL 이면
-- 충돌하지 않지만, 같은 양식에서 꺼내 만든 경우에는 막힌다.
drop index big_routines_template_id_and_routine_date_unique_when_not_deleted;

-- 양식을 꺼내 쓰는 순간 값이 복사되고 둘의 관계는 거기서 끝난다.
-- 어느 양식에서 나왔는지 기억할 이유가 없다.
--
-- 컬럼을 지우는 것이 이 규칙을 코드가 아니라 스키마로 강제한다.
-- 컬럼이 없으면 "양식을 고치면 이미 만든 루틴도 바뀌게" 구현할 자리가 없어진다.
alter table big_routines drop column template_id;

-- "자동 생성을 중단한다" 는 뜻이었는데 자동 생성 자체가 없어져 의미가 남지 않는다.
-- 양식을 더 안 쓰겠다는 표시는 deleted_at 하나로 충분하다.
alter table routine_templates drop column is_active;
