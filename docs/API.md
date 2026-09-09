# 예소 API 명세서

> 출처: Notion `예소 개발자용 > API 기본 명세서` (엔드포인트 DB) + `API Req/Res 명세 초안`
> 총 **46개** 엔드포인트 · 앱용 `/api/v1/**` 44개, 기기용 `/device-api/v1/**` 2개
> 커뮤니티 11개는 구현 제외이므로 **실제 구현 대상은 35개**입니다.
>
> 최종 수정: 2026-09-09 — **로그인 방식 전환**(소셜 → 이메일·비밀번호, JWT 제거)과 **루틴 반복 구조 확정**(빅루틴 즉시 생성, 템플릿 재정의)을 반영했습니다.
>
> **엔드포인트 증감 (2026-09-04 대비 48개 → 46개)**
>
> | 변화 | 엔드포인트 | 이유 |
> |---|---|---|
> | ➖ | `POST /auth/social-login` | 이메일·비밀번호로 전환 |
> | ➖ | `POST /auth/refresh` | 만료 없는 UUID 방식이라 재발급 대상 없음 |
> | ➖ | `GET /children/:childId/routines` | 캘린더가 한 달치 상세를 통째로 반환 |
> | ➖ | `POST /device-api/v1/token/refresh` | 기기 토큰 체계 삭제 |
> | ➕ | `POST /auth/signup` | 회원가입 |
> | ➕ | `POST /auth/login` | 로그인 |
>
> **문서 동기화**: `ERD.md`(`USERS`·`DEVICES`·`ROUTINE_TEMPLATES`·`BIG_ROUTINES` 컬럼, 4장 제약조건), `ROADMAP.md`(Phase 범위·블로커) 모두 **영향 있음**이며 같은 날짜로 함께 갱신했습니다.
> (직전 수정: 2026-09-04 — 날짜 동기화. 그 전: 2026-08-30)

> [!WARNING]
> **req/res 스키마는 초안입니다.** 엔드포인트 DB에는 `param` 컬럼(필드 이름 나열)까지만 존재하고, 각 엔드포인트 페이지의 Request/Response 템플릿은 비어 있습니다. 아래 스키마는 ERD 컬럼 타입과 `설명`·`기타` 컬럼에서 역산한 것으로, 팀 검토 후 확정해야 합니다. 추론 비중이 큰 항목은 🔺로 표시했습니다.

## 목차

1. [공통 규약](#1-공통-규약)
2. [공통 응답 포맷](#2-공통-응답-포맷)
3. [에러 코드](#3-에러-코드)
4. [인증](#4-인증)
5. [유저](#5-유저)
6. [자녀](#6-자녀)
7. [기기 (앱용)](#7-기기-앱용)
8. [디바이스 API (기기용)](#8-디바이스-api-기기용)
9. [루틴](#9-루틴)
10. [루틴 템플릿 (저장해둔 양식)](#10-루틴-템플릿-저장해둔-양식)
11. [대시보드](#11-대시보드)
12. [캐릭터](#12-캐릭터)
13. [커뮤니티](#13-커뮤니티)
14. [온보딩 & 페어링 호출 순서](#14-온보딩--페어링-호출-순서)
15. [확정 필요 항목](#15-확정-필요-항목)

---

## 1. 공통 규약

### 1-1. Base URL · 인증

**JWT 를 쓰지 않습니다**(2026-09-09 확정). 앱도 기기도 서버가 발급한 임의의 UUID 하나를 헤더에 담아 보내고, 서버는 그 값으로 DB 를 조회해 누구인지 알아냅니다. 서명 검증도 만료 처리도 재발급도 없습니다.

| 구분 | Base URL | 인증 방식 | 헤더 |
|---|---|---|---|
| 앱 (보호자) | `/api/v1` | `USERS.access_uuid` (로그인 시 발급) | `X-Access-Uuid: <uuid>` |
| 기기 | `/device-api/v1` | `DEVICES.device_access_uuid` (페어링 완료 시 발급) | `X-Device-Uuid: <uuid>` |

인증 불필요 엔드포인트: `POST /auth/signup`, `POST /auth/login`, `POST /device-api/v1/claim`

앱과 기기는 **읽는 헤더도 조회하는 테이블도 다르므로 SecurityFilterChain 을 2개로 분리**합니다. `/device-api/**` 에 앱용 필터가 걸리면 기기 요청이 유저를 찾다가 실패합니다.

### 1-1-1. 소유권 검사 (2026-09-09 확정)

인증은 "누구인가" 만 알려줍니다. **"이 자녀를 건드려도 되는가" 는 별개**이고, 경로에 `:childId` 가 들어가는 모든 엔드포인트가 이 검사를 합니다.

| 부르는 쪽 | 검사 방법 | 통과하지 못하면 |
|---|---|---|
| 앱 | `X-Access-Uuid` → `USERS` → 그 유저의 자녀 목록에 `:childId` 가 있는가 | `CHILD_FORBIDDEN` 403 |
| 기기 | `X-Device-Uuid` → `DEVICES` → 그 기기가 붙은 `child_id` 와 대상이 같은가 | `DEVICE_FORBIDDEN` 403 |

**없는 자녀와 남의 자녀를 구분해 응답합니다.** 없으면 `CHILD_NOT_FOUND` 404, 있지만 내 자녀가 아니면 `CHILD_FORBIDDEN` 403 입니다. 로그인한 사용자에게는 이 구분이 새어 나가도 문제가 없고, 앱이 "잘못된 요청" 과 "권한 없음" 을 다르게 안내할 수 있어야 하기 때문입니다.

기기가 자기 자녀 외의 대상을 부르는 것은 정상적인 앱·기기 동작에서 일어나지 않습니다. 그래도 검사하는 이유는, 기기의 `device_access_uuid` 가 새어 나갔을 때 그 값으로 **다른 아이의 루틴까지 읽히는 것을 막기 위해서**입니다.

> **만료가 없다는 것의 뜻** — 한 번 발급한 `access_uuid` 는 로그아웃하거나 값을 새로 발급하기 전까지 계속 유효합니다. 값이 새어 나가면 되찾을 방법이 로그아웃뿐입니다. 공모전 범위에서 **알고 받아들인 선택**이며, 그 대가로 서명 알고리즘 · 키 관리 · 만료 · 재발급 정책이 전부 사라졌습니다.

### 1-2. 표기 규칙

- 경로 변수는 이 문서에서 `:childId` 형태로 표기합니다. 실제 구현은 Spring 표준 중괄호 표기를 씁니다.
- 모든 요청·응답 바디는 `application/json; charset=utf-8`
- 필드명은 camelCase (DB 컬럼은 snake_case)

### 1-3. 날짜 · 시간 포맷

| 종류 | 포맷 | 예시 | 사용처 |
|---|---|---|---|
| date | `yyyy-MM-dd` | 2026-09-01 | routineDate, birthDate, from/to |
| time | `HH:mm` | 08:30 | startTime, endTime |
| timestamp | ISO-8601 UTC | 2026-09-01T08:30:00Z | createdAt, lastSyncedAt, serverTime |

> ✅ **확정 — 타임존 정책 (표준 UTC 방식).** 서버는 UTC 기준으로 저장하고(`timestamp` 계열은 `timestamptz`), 앱·기기가 표시·입력 시점에 로컬(KST)로 변환합니다. `time` 컬럼은 **로컬(KST) 벽시계 시각**으로 해석하며, 기기 RTC 보정(`serverTime`)과의 비교도 UTC 기준으로 맞춥니다. (ERD 5-2)

### 1-4. 페이지네이션

목록 조회 중 `GET /posts`만 해당합니다. 나머지 목록 API는 건수가 적어 전체 반환합니다.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "hasNext": true
}
```

---

## 2. 공통 응답 포맷

### 2-1. 성공

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

- 반환값이 없는 경우(삭제, 로그아웃 등)는 HTTP 204 + 바디 없음
- 목록 응답은 `data`에 배열 또는 페이지 객체를 담습니다

### 2-2. 실패

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CHILD_NOT_FOUND",
    "message": "자녀를 찾을 수 없습니다.",
    "details": null
  }
}
```

- `message`는 사용자 노출용 한국어 문구, `code`는 앱 분기 처리용 식별자. **앱은 message가 아니라 code로 분기해야 합니다.**
- `details`는 검증 실패 시에만 필드별 오류를 담습니다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_INPUT",
    "message": "입력값이 올바르지 않습니다.",
    "details": [
      { "field": "birthDate", "reason": "미래 날짜는 사용할 수 없습니다." }
    ]
  }
}
```

> ✅ **확정 (2026-09-09) — 기기 API 도 위의 공통 envelope 를 그대로 씁니다.** `/device-api/**` 만 flat 으로 가는 선택지는 채택하지 않았습니다.
>
> 기기 펌웨어가 `data` 한 겹을 더 들어가는 부담보다, **앱과 기기의 응답 처리 코드가 두 벌로 갈라지는 비용이 크다**고 보았습니다. 형태가 하나면 공통 응답 계층도 하나이고, 새 필드를 더할 때 한 곳만 고치면 됩니다.
>
> **다만 인증 실패 코드는 다릅니다.** 앱은 `UNAUTHORIZED`(3-1), 기기는 `DEVICE_UNAUTHORIZED`(3-4) 를 씁니다. 같은 코드를 쓰면 기기가 "재페어링이 필요한 상황" 과 "그냥 로그인이 필요한 상황" 을 구분할 수 없습니다. 기기는 앞엣것을 받으면 스스로 복구할 방법이 없어 사용자에게 재페어링을 안내해야 합니다.

### 2-3. HTTP 상태 코드 사용 규칙

| 코드 | 사용 시점 |
|---|---|
| 200 | 조회·수정 성공 |
| 201 | 생성 성공 |
| 204 | 삭제 성공, 응답 바디 없음 |
| 400 | 입력값 검증 실패, 잘못된 파라미터 |
| 401 | 토큰 없음·만료·위조 |
| 403 | 인증은 됐으나 해당 리소스 접근 권한 없음 |
| 404 | 리소스 없음, soft delete된 리소스 접근 |
| 409 | 상태 충돌 (이미 사용된 페어링 코드, 중복 좋아요, 기기 수 초과) |
| 410 | 페어링 코드 만료 |
| 500 | 서버 오류 |

---

## 3. 에러 코드

### 3-1. 공통

| code | HTTP | message |
|---|---|---|
| `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. |
| `UNAUTHORIZED` | 401 | 로그인이 필요합니다. |
| `FORBIDDEN` | 403 | 접근 권한이 없습니다. |
| `NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 허용되지 않은 요청 방식입니다. |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했습니다. |

### 3-2. 인증 · 유저

| code | HTTP | message | 발생 지점 |
|---|---|---|---|
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일입니다. | signup |
| `AUTH_INVALID_EMAIL_FORMAT` | 400 | 이메일 형식이 올바르지 않습니다. | signup |
| `AUTH_INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | login |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. | users/me |
| `USER_ALREADY_WITHDRAWN` | 409 | 이미 탈퇴한 계정입니다. | users/me |

> **`AUTH_INVALID_CREDENTIALS` 는 이메일이 없을 때와 비밀번호가 틀릴 때 모두 같은 메시지를 씁니다.** 둘을 구분해 알려주면 "이 이메일은 가입되어 있다" 는 사실이 새어 나갑니다.

> **헤더가 없거나 유효하지 않은 경우**는 3-1 의 공통 코드 `UNAUTHORIZED`(401, "로그인이 필요합니다.")를 씁니다. 인증 실패마다 새 코드를 만들면 같은 뜻의 코드가 둘이 되어 앱이 어느 쪽을 봐야 할지 모르게 됩니다.

### 3-3. 자녀

| code | HTTP | message | 비고 |
|---|---|---|---|
| `CHILD_NOT_FOUND` | 404 | 자녀를 찾을 수 없습니다. | |
| `CHILD_FORBIDDEN` | 403 | 해당 자녀에 대한 권한이 없습니다. | |
| `CHILD_LIMIT_EXCEEDED` | 409 | 등록 가능한 자녀 수를 초과했습니다. | **보호자당 10명** (2026-09-04 확정) |

### 3-4. 기기 · 페어링

| code | HTTP | message | 비고 |
|---|---|---|---|
| `DEVICE_NOT_FOUND` | 404 | 기기를 찾을 수 없습니다. | |
| `DEVICE_FORBIDDEN` | 403 | 해당 기기에 대한 권한이 없습니다. | |
| `DEVICE_LIMIT_EXCEEDED` | 409 | 자녀당 등록 가능한 기기 수를 초과했습니다. | **자녀당 10대** (2026-09-04 확정) |
| `PAIRING_CODE_NOT_FOUND` | 404 | 유효하지 않은 페어링 코드입니다. | claim |
| `PAIRING_CODE_EXPIRED` | 410 | 페어링 코드가 만료되었습니다. | 발급 후 10분 |
| `PAIRING_CODE_ALREADY_USED` | 409 | 이미 사용된 페어링 코드입니다. | 일회용 |
| `DEVICE_UID_ALREADY_PAIRED` | 409 | 이미 다른 계정에 연결된 기기입니다. | `unique(device_uid)` 위반 |
| `DEVICE_UNAUTHORIZED` | 401 | 기기 인증에 실패했습니다. | `X-Device-Uuid` 없음·불일치. **자체 복구 경로가 없어 재페어링 안내** |

### 3-5. 루틴

| code | HTTP | message |
|---|---|---|
| `BIG_ROUTINE_NOT_FOUND` | 404 | 루틴을 찾을 수 없습니다. |
| `SMALL_ROUTINE_NOT_FOUND` | 404 | 할 일을 찾을 수 없습니다. |
| `ROUTINE_TEMPLATE_NOT_FOUND` | 404 | 저장해둔 루틴 양식을 찾을 수 없습니다. |
| `ROUTINE_INVALID_TIME_RANGE` | 400 | 종료 시각이 시작 시각보다 빠를 수 없습니다. |
| `ROUTINE_INVALID_DATE_RANGE` | 400 | 종료일이 시작일보다 빠를 수 없습니다. |
| `ROUTINE_DATE_RANGE_TOO_LONG` | 400 | 한 번에 등록할 수 있는 기간을 초과했습니다. |
| `ROUTINE_INVALID_REPEAT_RULE` | 400 | 반복 설정이 올바르지 않습니다. |
| `ROUTINE_TOO_MANY_DATES` | 400 | 한 번에 지정할 수 있는 날짜 수를 초과했습니다. |
| `ROUTINE_ORDER_MISMATCH` | 400 | 정렬 대상이 올바르지 않습니다. |

### 3-6. 커뮤니티 · 캐릭터

> **커뮤니티 7개 코드는 `ErrorCode` 열거형에 넣지 않습니다** (2026-09-04 확정). 커뮤니티가 구현 제외라 이 코드들을 던질 API 가 만들어지지 않기 때문입니다. 아래 표에 ❌ 로 표시했으며, 코드 쪽에는 `ErrorCode` 상단 주석으로 제외 사실만 남깁니다. 향후 커뮤니티를 구현하게 되면 이 표를 그대로 옮기면 됩니다.
>
> `CHARACTER_NOT_FOUND` 는 **캐릭터용이고 캐릭터는 구현 대상**이므로 제외 대상이 아닙니다.

| code | HTTP | message | 구현 |
|---|---|---|---|
| `POST_NOT_FOUND` | 404 | 게시글을 찾을 수 없습니다. | ❌ 제외 |
| `POST_FORBIDDEN` | 403 | 본인이 작성한 글만 수정·삭제할 수 있습니다. | ❌ 제외 |
| `COMMENT_NOT_FOUND` | 404 | 댓글을 찾을 수 없습니다. | ❌ 제외 |
| `COMMENT_FORBIDDEN` | 403 | 본인이 작성한 댓글만 수정·삭제할 수 있습니다. | ❌ 제외 |
| `COMMENT_DEPTH_EXCEEDED` | 400 | 대댓글에는 답글을 달 수 없습니다. | ❌ 제외 |
| `LIKE_ALREADY_EXISTS` | 409 | 이미 좋아요한 게시글입니다. | ❌ 제외 |
| `LIKE_NOT_FOUND` | 404 | 좋아요 기록이 없습니다. | ❌ 제외 |
| `CHARACTER_NOT_FOUND` | 404 | 캐릭터를 찾을 수 없습니다. | ✅ |

---

## 4. 인증

> ✅ **확정 (2026-09-09) — 이메일 · 비밀번호 로그인.** 소셜 로그인(`POST /auth/social-login`)과 토큰 재발급(`POST /auth/refresh`)은 **삭제**되었습니다. 공모전 일정에 맞춰 로그인을 최소로 줄인 결정입니다.
>
> ⚠️ **비밀번호 찾기와 이메일 인증은 구현하지 않습니다.** 메일을 보낼 수단이 로드맵에 없기 때문입니다. 이메일은 **형식만** 확인하고 실제로 도달하는 주소인지 확인하지 않습니다. 따라서 **비밀번호를 잊으면 스스로 계정을 되찾을 수 없습니다.**

### POST /auth/signup — 회원가입

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| email | string | Y | 형식 검사만 수행. 실제 발송·인증 없음 |
| password | string | Y | **최소 8자.** 문자 조합 규칙은 두지 않음 |

**Response 201**

```json
{
  "success": true,
  "data": {
    "accessUuid": "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d",
    "user": { "userId": 1, "name": null, "email": "parent@example.com" }
  },
  "error": null
}
```

- 가입과 동시에 로그인 상태가 됩니다. 가입 직후 다시 로그인시키는 화면은 필요 없습니다.
- `name` 이 `null` 이면 앱은 온보딩 1차(보호자 성명 입력)로 분기합니다. 소셜 로그인 시절의 `isFirstLogin` 필드를 대신합니다. 가입과 로그인이 분리되어 "이번이 첫 로그인인가" 를 서버가 따로 알려줄 이유가 없어졌기 때문입니다.
- 비밀번호는 **되돌릴 수 없는 형태로 바꿔** `USERS.password_hash` 에 저장합니다. 평문은 로그를 포함해 어디에도 남기지 않습니다.

**Error**: `AUTH_INVALID_EMAIL_FORMAT` 400, `INVALID_INPUT` 400(비밀번호가 8자 미만), `AUTH_EMAIL_ALREADY_EXISTS` 409

### POST /auth/login — 로그인

**Request**: `email` (string, Y), `password` (string, Y)

**Response 200**

```json
{
  "success": true,
  "data": {
    "accessUuid": "3f2b8c10-5d4e-4a91-b7c3-9e0f1a2b3c4d",
    "user": { "userId": 1, "name": "김보호", "email": "parent@example.com" }
  },
  "error": null
}
```

- 로그인할 때마다 `access_uuid` 를 **새로 발급하고 기존 값을 무효로 만듭니다.** 그래서 한 계정은 항상 한 기기에서만 로그인 상태입니다. 값을 여러 개 두려면 별도 테이블이 필요한데, 지금 범위에서는 컬럼 하나로 끝내는 편이 낫다고 판단했습니다.
- 앱은 이 값을 저장해 두고 이후 모든 요청에 `X-Access-Uuid` 헤더로 담습니다.

**Error**: `AUTH_INVALID_CREDENTIALS` 401 (이메일이 없을 때와 비밀번호가 틀릴 때 **같은 응답**)

### POST /auth/logout — 로그아웃

`USERS.access_uuid` 를 비웁니다. 바디 없음, `X-Access-Uuid` 헤더로 식별합니다.

**Response 204**

- 이 값이 비워지면 그 값을 들고 있던 요청은 전부 `UNAUTHORIZED` 를 받습니다. 만료가 없는 구조라 **로그아웃이 값을 무효로 만드는 유일한 수단**입니다.

---

## 5. 유저

### GET /users/me — 내 정보 조회

**Response 200**

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "name": "김보호",
    "email": "parent@example.com",
    "provider": "GOOGLE",
    "createdAt": "2026-09-01T08:30:00Z"
  },
  "error": null
}
```

### PATCH /users/me — 내 정보 수정

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 보호자 성명. 온보딩 1차에서도 이 API 사용 |

**Response 200**: `GET /users/me`와 동일

### DELETE /users/me — 회원 탈퇴

**Response 204**

> 🚧 **정책 미확정으로 구현 보류.** 최소한 (1) 페어링된 기기의 토큰 무효화, (2) 자녀 soft delete 연쇄, (3) 작성한 게시글·댓글 유지 여부 세 가지 결정이 필요합니다.

---

## 6. 자녀

### POST /children — 자녀 등록 (온보딩 2차)

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 자녀 이름 |
| birthDate | date | Y | yyyy-MM-dd. 미래 날짜 불가 |
| relationship | enum | Y | `PARENT`(부모) · `ADMIN`(관리자) **2개만** |

> ✅ **확정 (2026-09-04) — `relationship` 값 목록.** `PARENT`(부모)와 `ADMIN`(관리자) **두 개로 제한**합니다.
>
> 한국어 "부모"·"관리자"를 코드값으로 옮긴 것입니다. 다른 표기를 원하시면 이 표와 아래 예시, 그리고 코드의 열거형을 함께 고치면 됩니다.
>
> 이 값은 **임시로 정한 것**입니다. 타겟이 "일반학교 특수학급"으로 확장되면 교사 관련 값이 필요해질 수 있습니다(ERD 7장). 그때는 값을 늘리면 되고 이미 저장된 데이터는 그대로 둘 수 있습니다.

**Response 201**

```json
{
  "success": true,
  "data": {
    "childId": 10,
    "name": "김아이",
    "birthDate": "2018-03-02",
    "relationship": "PARENT",
    "createdAt": "2026-09-01T08:30:00Z"
  },
  "error": null
}
```

### GET /children — 자녀 목록 조회

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "childId": 10,
      "name": "김아이",
      "birthDate": "2018-03-02",
      "relationship": "PARENT",
      "deviceCount": 2
    }
  ],
  "error": null
}
```

- 🔺 `deviceCount`는 추론 항목. N기기 전환으로 목록 화면에서 기기 연결 여부를 보여줄 필요가 생겼는데 명세에는 없습니다.

### GET /children/:childId — 자녀 상세 조회

**Response 200**

```json
{
  "success": true,
  "data": {
    "childId": 10,
    "name": "김아이",
    "birthDate": "2018-03-02",
    "relationship": "PARENT",
    "createdAt": "2026-09-01T08:30:00Z"
  },
  "error": null
}
```

> 🔵 **N기기 변경 영향**: 1기기 시절에는 이 응답에 기기 정보를 끼워 넣는 안이 가능했지만, 이제 기기는 `GET /children/:childId/devices`로 분리됐습니다. 이 응답에는 기기 정보를 넣지 않습니다.

### PATCH /children/:childId — 자녀 정보 수정

**Request**: `name`, `birthDate`, `relationship` (모두 선택, 최소 1개 필요)

**Response 200**: 상세 조회와 동일

### DELETE /children/:childId — 자녀 삭제

**Response 204** — soft delete. 연결된 기기의 페어링 해제와 토큰 무효화가 함께 일어나야 합니다.

**Error**: `CHILD_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403

---

## 7. 기기 (앱용)

### POST /devices/pairing — 페어링 코드 발급

온보딩 3차의 시작점. 핫스팟 접속 전(인터넷이 살아 있을 때) 호출합니다.

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| childId | number | Y | 기기를 연결할 자녀 |
| nickname | string | N | 기기 별칭. 미입력 시 서버가 기본값 부여 |

**Response 201**

```json
{
  "success": true,
  "data": {
    "deviceId": 55,
    "pairingCode": "482913",
    "expiresAt": "2026-09-01T08:40:00Z",
    "status": "PENDING"
  },
  "error": null
}
```

> 🔵 **[N기기 변경]** 응답에 `deviceId` 추가. 자녀당 PENDING 행이 여러 개 생길 수 있어 `childId`만으로는 방금 만든 행을 특정할 수 없고, 온보딩 마지막 폴링(`GET /devices/:deviceId`)에 이 값이 필요합니다.

- `deviceUid`는 받지 않습니다. 기기가 claim 시 직접 전달합니다.
- 🔺 `pairingCode` 자릿수·문자 구성 미정. 기기 입력 UI가 없고 핫스팟으로 전달되므로 6자리보다 길어도 무방합니다. 자녀당 PENDING이 여러 개일 때의 충돌 확률을 고려해야 합니다.

**Error**: `CHILD_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403, `DEVICE_LIMIT_EXCEEDED` 409

### GET /children/:childId/devices — 자녀 기기 목록 조회

> 🔵 **[N기기 변경] 신설.** 설정창 기기 관리 화면의 진입점.

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "deviceId": 55,
      "nickname": "아이 시계",
      "status": "ACTIVE",
      "battery": 78,
      "firmwareVersion": "1.0.3",
      "lastSyncedAt": "2026-09-01T07:55:00Z",
      "pairedAt": "2026-08-20T10:12:00Z"
    },
    {
      "deviceId": 56,
      "nickname": "예비 기기",
      "status": "PENDING",
      "battery": null,
      "firmwareVersion": null,
      "lastSyncedAt": null,
      "pairedAt": null
    }
  ],
  "error": null
}
```

- PENDING 상태 기기는 아직 claim 전이라 `battery`·`firmwareVersion`·`lastSyncedAt`이 모두 null입니다. **앱은 이 null을 반드시 처리해야 합니다.**

### GET /devices/:deviceId — 기기 상태 조회

온보딩 마지막 단계에서 `PENDING → ACTIVE` 전환을 폴링하는 용도로도 사용합니다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "deviceId": 55,
    "childId": 10,
    "nickname": "아이 시계",
    "status": "ACTIVE",
    "battery": 78,
    "firmwareVersion": "1.0.3",
    "lastSyncedAt": "2026-09-01T07:55:00Z",
    "pairedAt": "2026-08-20T10:12:00Z"
  },
  "error": null
}
```

- `status`: `PENDING` | `ACTIVE`
- `lastSyncedAt`은 필수 반환 항목. 배터리 값은 이 시점 기준값입니다.
- 🔺 폴링 주기·타임아웃은 앱 구현 사항이나 서버와 합의 필요. 2초 간격 / 최대 3분을 제안하며, 타임아웃 시 앱은 페어링 재시도 화면으로 보내야 합니다.

### PATCH /devices/:deviceId — 기기 정보 수정

**Request**: `nickname` (string, Y)

**Response 200**: 기기 상태 조회와 동일

### DELETE /devices/:deviceId — 기기 연결 해제

**Response 204**

- soft delete + 토큰 무효화. `unique(device_uid) where deleted_at is null` 제약 덕분에 같은 기기를 나중에 재페어링할 수 있습니다.

**Error**: `DEVICE_NOT_FOUND` 404, `DEVICE_FORBIDDEN` 403

---

## 8. 디바이스 API (기기용)

> ⚠️ 이 구간은 앱과 인증 방식이 다릅니다. 기기는 `X-Device-Uuid` 헤더에 `DEVICES.device_access_uuid` 를 담습니다. SecurityFilterChain 을 분리해 `/device-api/**` 에 앱용 필터가 걸리지 않게 해야 합니다. 앱용 필터가 걸리면 기기 요청이 `USERS` 에서 유저를 찾다가 실패합니다.

> ✅ **확정 (2026-09-09)** — 기기 토큰 체계를 없앴습니다. `POST /device-api/v1/token/refresh` 는 **삭제**되었고 `token` · `secret` 도 사라졌습니다. 페어링이 끝나면 서버가 발급하는 `deviceAccessUuid` 하나만 씁니다.

### POST /device-api/v1/claim — 기기 등록

기기가 집 와이파이에 접속한 뒤 최초 1회 호출합니다. **인증 헤더 없음.**

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| pairingCode | string | Y | 앱이 핫스팟으로 전달한 일회용 코드 |
| deviceUid | string | Y | 기기가 생성한 고유 식별자 |
| firmware | string | Y | 펌웨어 버전 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "deviceId": 55,
    "deviceAccessUuid": "7c9e4d21-8b3a-4f60-a1d5-2e8c0b7f3a94",
    "serverTime": "2026-09-01T08:35:12Z"
  },
  "error": null
}
```

**서버 처리 순서**

1. `pairingCode`로 `status = 'PENDING'`인 행 조회 (`unique(pairing_code) where status = 'PENDING'` 덕분에 단일 행 특정)
2. 만료 시각 검증 (발급 후 10분)
3. `device_uid`, `paired_at` 채우고 `status = 'ACTIVE'`
4. **서버가 `device_access_uuid` 를 새로 만들어** 저장 (기기가 만든 값을 쓰지 않는 이유는 ERD 설계 노트 참고)
5. `pairing_code` 를 NULL 처리 (일회용)
6. `deviceAccessUuid` 반환 — **기기가 반드시 영속 저장해야 합니다.** 잃어버리면 되찾을 경로가 없어 재페어링뿐입니다.

- 🔺 `serverTime`은 추론 항목. claim 직후 RTC를 맞춰두면 첫 sync 전까지의 시각 오차를 줄일 수 있어 추가했습니다.

**Error**: `PAIRING_CODE_NOT_FOUND` 404, `PAIRING_CODE_EXPIRED` 410, `PAIRING_CODE_ALREADY_USED` 409, `DEVICE_UID_ALREADY_PAIRED` 409

### ~~POST /device-api/v1/token/refresh~~ — ❌ 삭제 (2026-09-09)

기기 토큰 체계를 없애면서 재발급할 대상이 사라졌습니다. `secret` 도 함께 없어졌습니다.

**대신 `deviceAccessUuid` 를 잃어버리면 재페어링해야 합니다.** 재페어링 없이 복구하는 경로가 사라진 것이 이 결정의 대가입니다. 기기가 이 값을 지우지 않는 저장소에 넣는 것이 그만큼 중요해졌습니다.

### POST /device-api/v1/sync — 기기 동기화 (push + pull)

완료 기록·배터리 업로드와 하루치 루틴 다운로드를 한 번에 처리합니다.

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| battery | number | Y | 0~100 |
| firmware | string | Y | 펌웨어 버전. 변경 시 서버가 갱신 |
| completions | array | Y | 완료 기록. 없으면 빈 배열 |
| dates | array | Y | 받아올 루틴 날짜 목록 (yyyy-MM-dd) |

```json
{
  "battery": 78,
  "firmware": "1.0.3",
  "completions": [
    { "smallRoutineId": 901, "status": "DONE", "completedAt": "2026-09-01T07:42:00Z" }
  ],
  "dates": ["2026-09-01", "2026-09-02"]
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "serverTime": "2026-09-01T08:35:12Z",
    "accepted": 2,
    "routines": [
      {
        "date": "2026-09-01",
        "bigRoutines": [
          {
            "bigRoutineId": 300,
            "title": "아침 준비",
            "startTime": "07:30",
            "endTime": "08:30",
            "sortOrder": 1,
            "smallRoutines": [
              { "smallRoutineId": 901, "title": "세수하기", "sortOrder": 1, "status": "DONE" },
              { "smallRoutineId": 903, "title": "양치하기", "sortOrder": 2, "status": "PENDING" }
            ]
          }
        ]
      }
    ]
  },
  "error": null
}
```

**구현 포인트**

- 응답 `serverTime`으로 기기 RTC를 보정합니다.
- **이 엔드포인트는 루틴을 만들지 않습니다.** 있는 것만 읽어 갑니다. 기기에서는 루틴 생성이 불가능하고, 빅루틴은 앱이 요청할 때 즉시 만들어지기 때문입니다(9장).
- UPDATE 기반이라 **멱등성이 보장**됩니다. 네트워크 실패로 기기가 같은 `completions`를 재전송해도 안전합니다.
- 🔺 `accepted` 카운트와 부분 실패 처리 방식은 추론. 일부 `smallRoutineId`가 이미 삭제된 경우 전체를 실패시킬지 무시할지 정해야 합니다. **무시하고 넘어가는 쪽을 권장** — 기기는 재시도 외에 할 수 있는 일이 없습니다.
- 🔺 `dates` 배열 최대 길이 제한 필요. 3일을 제안합니다.

**Error**: `DEVICE_UNAUTHORIZED` 401 (`X-Device-Uuid` 가 없거나 유효하지 않음. 자체 복구 경로가 없으므로 기기는 재페어링을 안내해야 합니다)

---

## 9. 루틴

> ✅ **확정 (2026-09-09)** — 반복 생성은 **빅루틴이 직접** 처리하고 **전부 즉시 만듭니다.** 조회 시점에 만들어내던 "지연 생성" 은 없어졌습니다(ERD 5-1). 템플릿은 반복과 무관한 **저장해둔 양식**으로 역할이 바뀌었습니다(10장).

### POST /children/:childId/big-routines — 빅루틴 생성

반복 모드에 따라 날짜 목록을 펼쳐 **날짜별 행을 즉시 만들고, 전부 같은 `series_id` 를 부여**합니다.

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| title | string | Y | 빅루틴 제목 |
| startTime | time | Y | HH:mm |
| endTime | time | Y | HH:mm. startTime 보다 뒤여야 함 |
| repeatType | enum | Y | `RANGE` \| `WEEKLY` \| `DATES`. **셋 중 하나만** |
| startDate | date | 조건부 | `RANGE` · `WEEKLY` 필수 |
| endDate | date | 조건부 | `RANGE` · `WEEKLY` 필수 |
| repeatDays | array | 조건부 | `WEEKLY` 필수. `MON`~`SUN` |
| repeatDates | array | 조건부 | `DATES` 필수. **최대 12개** |
| smallRoutines | array | N | 내부 할 일 목록 |
| templateId | number | N | 저장해둔 양식에서 꺼내 만들 때. 값이 복사될 뿐 이후 연결은 남지 않음 |

**반복 모드 세 가지** — 서로 배타적입니다. 하나만 고릅니다.

| repeatType | 뜻 | 필요한 필드 |
|---|---|---|
| `RANGE` | 기간 안의 매일 | `startDate`, `endDate` |
| `WEEKLY` | 기간 안에서 지정한 요일마다 | `startDate`, `endDate`, `repeatDays` |
| `DATES` | 지정한 날짜들만 | `repeatDates` |

**하루짜리 루틴**은 `RANGE` 에 `startDate` 와 `endDate` 를 같은 날로 주면 됩니다. 별도 모드를 두지 않았습니다.

```json
{
  "title": "아침 준비",
  "startTime": "07:30",
  "endTime": "08:30",
  "repeatType": "WEEKLY",
  "startDate": "2026-09-01",
  "endDate": "2026-09-30",
  "repeatDays": ["MON", "WED", "FRI"],
  "smallRoutines": [
    { "title": "세수하기", "order": 1 },
    { "title": "양치하기", "order": 2 }
  ]
}
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "seriesId": "9f1c2e40-...",
    "createdDates": ["2026-09-02", "2026-09-04", "2026-09-07"],
    "createdCount": 13
  },
  "error": null
}
```

- **반복 모드는 저장되지 않습니다.** 날짜를 펼치는 데에만 쓰이고, 행이 만들어진 뒤에는 `series_id` 로 묶여 있다는 사실만 남습니다. 그래서 `BIG_ROUTINES` 에 반복 관련 컬럼이 하나도 없습니다.
- **단일 루틴과 복합 루틴을 서버는 구분하지 않습니다.** 스몰루틴이 1개면 단일, 여러 개면 복합입니다. **앱이 `smallRoutines` 배열 길이로 판단하세요.** 서버는 `routineType` 같은 필드를 주지 않습니다.
- `WEEKLY` 는 기간 안에 해당 요일이 하루도 없으면 아무것도 만들지 않고 `createdCount: 0` 을 돌려줍니다. 오류가 아닙니다.

**Error**: `ROUTINE_INVALID_TIME_RANGE`, `ROUTINE_INVALID_DATE_RANGE`, `ROUTINE_DATE_RANGE_TOO_LONG`, `ROUTINE_INVALID_REPEAT_RULE`(모드에 필요한 필드가 없거나 `repeatDays` 가 빈 배열), `ROUTINE_TOO_MANY_DATES`(`repeatDates` 13개 이상) — 모두 400. 그 밖에 `CHILD_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403 (1-1-1)

> 🔺 **`RANGE` 기간 길이 상한 미확정** (15장 #7). 상한이 없으면 한 번의 요청으로 몇 년치 행이 생길 수 있어 **설정값으로 빼 둡니다.** `DATES` 의 12개와는 **별개 값**입니다.

### GET /children/:childId/calendar — 캘린더 조회

**한 달치 루틴을 상세까지 통째로** 반환합니다. 앱은 이 응답을 들고 있다가 사용자가 날짜를 누르면 **서버를 다시 부르지 않고** 가지고 있는 값에서 그 날짜를 꺼내 보여줍니다.

**Query**: `from` (date, Y), `to` (date, Y)

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "date": "2026-09-01",
      "totalCount": 8,
      "doneCount": 6,
      "completionRate": 75.0,
      "bigRoutines": [
        {
          "bigRoutineId": 300,
          "seriesId": "9f1c2e40-...",
          "title": "아침 준비",
          "startTime": "07:30",
          "endTime": "08:30",
          "sortOrder": 1,
          "smallRoutines": [
            {
              "smallRoutineId": 901,
              "seriesId": "3a7b...",
              "title": "세수하기",
              "sortOrder": 1,
              "status": "DONE",
              "completedAt": "2026-09-01T07:42:00Z"
            }
          ]
        }
      ]
    }
  ],
  "error": null
}
```

- **`GET /children/:childId/routines`(날짜별 루틴 조회)는 삭제되었습니다.** 이 응답에 이미 날짜별 상세가 다 들어 있어 서버를 한 번 더 부를 이유가 없습니다.
- **이 엔드포인트는 더 이상 생성을 하지 않습니다.** 순수하게 읽기만 합니다. 즉시 생성으로 바뀌면서 조회 시점에 만들 것이 없어졌습니다.
- `status`: `PENDING` \| `DONE`
- ⚠️ **앱이 들고 있는 값은 시간이 지나면 실제와 어긋납니다.** 아이가 기기에서 루틴을 완료해도 앱은 알지 못합니다. 앱은 화면에 다시 들어올 때 이 API 를 다시 부르세요.
- 🔺 `from`~`to` 최대 범위 상한 미확정 (15장 #7). 31일을 제안합니다. 상세까지 담으므로 응답이 커집니다.

**Error**: `CHILD_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403 (1-1-1)

### PATCH /big-routines/:bigRoutineId — 빅루틴 수정

**Request**: `title`, `startTime`, `endTime` (모두 선택, 최소 1개)

**Query**: `scope` — `series`(같은 series 전체, **기본값**) \| `single`(해당 날짜만)

**Response 200**: 빅루틴 단건

- **기본이 `series` 입니다.** 반복으로 만든 루틴은 사용자 눈에 하나이므로, 월요일만 바뀌고 수 · 금이 그대로면 이상합니다. 삭제(`scope` 기본 `single`)와 기본값이 반대인 것은 의도한 것입니다. **수정은 되돌릴 수 있지만 삭제는 어렵기 때문입니다.**
- ⚠️ **오늘보다 이전 날짜의 행은 바꾸지 않습니다.** `scope=series` 여도 그렇습니다. 지난 기록은 그때 실제로 무엇을 하기로 했었는지를 담고 있어야 합니다. 여기서 "오늘" 은 **KST 기준 오늘 날짜**입니다(1-3).

**Error**: `BIG_ROUTINE_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403

- 경로에 `:childId` 가 없어도 소유권 검사를 합니다. 빅루틴에서 자녀를 거슬러 올라가 그 자녀가 내 자녀인지 봅니다. 이 검사가 없으면 `bigRoutineId` 를 1, 2, 3 으로 바꿔가며 **남의 아이 루틴을 전부 고칠 수 있습니다.**

### DELETE /big-routines/:bigRoutineId — 빅루틴 삭제

**Query**: `scope` — `single` (해당 날짜만, **기본값**) \| `series` (같은 series 전체)

**Response 204**

- soft delete. 이행률 통계 보존을 위해 물리 삭제하지 않습니다.
- `scope` 기본값은 `single` 입니다. 실수로 전체가 지워지는 것보다 안전합니다.
- `scope=series` 도 **오늘 이후 날짜만** 지웁니다. 지나간 기록은 남습니다.

### POST /big-routines/:bigRoutineId/small-routines — 스몰루틴 추가

**Request**: `title` (string, Y), `order` (number, N)

**Query**: `scope` — `series`(같은 series 전체, **기본값**) \| `single`(해당 날짜만)

**Response 201**

- ⚠️ **오늘보다 이전 날짜에는 추가하지 않습니다.** 할 일 개수가 늘면 그 날의 이행률 분모가 커져 **이미 지나간 날의 성적이 떨어집니다.** 아이가 아무것도 하지 않았는데 지난주 이행률이 나빠지는 셈입니다. 삭제도 같습니다.

```json
{
  "success": true,
  "data": {
    "smallRoutineId": 905,
    "seriesId": "b2d9...",
    "title": "가방 챙기기",
    "sortOrder": 3,
    "status": "PENDING"
  },
  "error": null
}
```

- **새 `series_id`가 발급됩니다.** 새로운 미션으로 간주하기 때문입니다.

### PUT /big-routines/:bigRoutineId/small-routines/order — 스몰루틴 순서 변경

드래그 정렬 UI 대응. 연속적인 순서를 일괄 재정렬합니다.

**Request**

```json
[
  { "smallRoutineId": 903, "order": 1 },
  { "smallRoutineId": 901, "order": 2 },
  { "smallRoutineId": 905, "order": 3 }
]
```

**Response 200**: 재정렬된 스몰루틴 목록

- 해당 빅루틴의 스몰루틴 **전체**가 배열에 포함되어야 합니다. 누락이 있으면 `ROUTINE_ORDER_MISMATCH` 400.
- 🔺 envelope 규칙상 요청 최상위가 배열인 것이 어색합니다. `{ "orders": [...] }` 형태로 감싸는 편이 일관적입니다.

### PATCH /small-routines/:smallRoutineId — 스몰루틴 수정

**Request**: `title` (string, Y)

**Response 200**: 스몰루틴 단건

- `series_id`를 **유지**합니다. 같은 미션의 이름 변경으로 간주하기 때문입니다.

### DELETE /small-routines/:smallRoutineId — 스몰루틴 삭제

**Response 204** — soft delete (이행률 통계 보존)

---

## 10. 루틴 템플릿 (저장해둔 양식)

> ✅ **재정의 (2026-09-09)** — 템플릿은 **자주 쓰는 루틴을 저장해두고 나중에 꺼내 쓰는 양식**입니다. 자동으로 빅루틴을 만들지 않습니다.
>
> 이전에는 "고정 반복 루틴의 원본" 이었고 조회 시점에 빅루틴을 만들어내는 구조였습니다. 반복 생성이 빅루틴 쪽으로 옮겨가면서(9장) 그 역할이 통째로 사라졌습니다. 함께 없어진 것: 지연 생성, `is_active`, `BIG_ROUTINES.template_id`, `unique(template_id, routine_date)` 제약.

### POST /children/:childId/routine-templates — 양식 저장

**Request**

```json
{
  "title": "저녁 루틴",
  "startTime": "19:00",
  "endTime": "20:00",
  "smallRoutines": [
    { "title": "숙제하기", "order": 1 },
    { "title": "책 읽기", "order": 2 }
  ]
}
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "templateId": 12,
    "title": "저녁 루틴",
    "startTime": "19:00",
    "endTime": "20:00",
    "smallRoutines": [
      { "title": "숙제하기", "order": 1 },
      { "title": "책 읽기", "order": 2 }
    ]
  },
  "error": null
}
```

- 템플릿의 `smallRoutines` 는 ERD 상 `jsonb` 컬럼입니다. 별도 테이블이 아니므로 개별 id 가 없습니다.
- **반복 관련 필드가 없습니다.** 반복은 빅루틴을 만들 때 정합니다(9장 `repeatType`).
- 응답에 `isActive` 가 없습니다. 컬럼이 삭제되었습니다.

**Error**: `CHILD_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403 (1-1-1)

### GET /children/:childId/routine-templates — 양식 목록

삭제되지 않은(`deleted_at is null`) 양식을 반환합니다.

**Response 200**: 템플릿 배열

### PATCH /routine-templates/:templateId — 양식 수정

**Request**: `title`, `startTime`, `endTime`, `smallRoutines` (모두 선택)

**Response 200**: 템플릿 단건

> ⚠️ **이미 만들어진 빅루틴은 바뀌지 않습니다.** 양식을 꺼내 쓰는 순간 값이 복사되고 둘의 연결은 거기서 끝나기 때문입니다. 워드의 서식 파일을 고쳐도 이미 만든 문서는 그대로인 것과 같습니다.
>
> 반면 **빅루틴을 고치면 같은 `series_id` 의 다른 날짜도 함께 바뀝니다**(9장). 둘이 다르게 동작하므로 앱에서 사용자에게 구분해 안내해야 혼란이 없습니다.

### DELETE /routine-templates/:templateId — 양식 삭제

**Response 204**

- soft delete (`deleted_at` 채움). 이 양식으로 이미 만들어 둔 빅루틴은 **그대로 남습니다.**

**Error**: `ROUTINE_TEMPLATE_NOT_FOUND` 404, `CHILD_FORBIDDEN` 403 — `:templateId` 만 받는 엔드포인트도 양식에서 자녀를 거슬러 올라가 검사합니다.

---

## 11. 대시보드

### GET /children/:childId/dashboard — 대시보드 조회

내 기기 메인창. 이번주·어제 이행률, 기기 상태, 인사이트를 통합 조회합니다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "childId": 10,
    "childName": "김아이",
    "thisWeek": { "completionRate": 82.5, "doneCount": 33, "totalCount": 40 },
    "yesterday": { "completionRate": 75.0, "doneCount": 6, "totalCount": 8 },
    "devices": [
      {
        "deviceId": 55,
        "nickname": "아이 시계",
        "battery": 78,
        "status": "ACTIVE",
        "lastSyncedAt": "2026-09-01T07:55:00Z"
      }
    ],
    "insights": [
      { "type": "LOW_MISSION_RATE", "message": "양치하기 미션 이행률이 낮아요.", "seriesId": "3a7b..." }
    ]
  },
  "error": null
}
```

> 🔴 **Breaking change.** `battery` 단일 필드가 `devices` 배열로 바뀝니다. 기기가 여러 대일 때 단일 battery 값이 어느 기기 것인지 표현할 수 없기 때문입니다. **앱과 동시 배포가 필요합니다.**

- 🔺 `insights` 구조는 추론. 인사이트 종류와 생성 규칙이 정해지지 않았습니다. 우선 `LOW_MISSION_RATE` 하나만 두고 `type`으로 확장하는 형태를 제안합니다.

### GET /children/:childId/stats — 기간별 이행률

**Query**: `from` (date, Y), `to` (date, Y)

**Response 200**

```json
{
  "success": true,
  "data": {
    "from": "2026-08-01",
    "to": "2026-08-31",
    "summary": { "completionRate": 78.3, "doneCount": 188, "totalCount": 240 },
    "daily": [
      { "date": "2026-08-01", "completionRate": 87.5, "doneCount": 7, "totalCount": 8 }
    ]
  },
  "error": null
}
```

- 집계 테이블 없이 `small_routines`를 직접 집계합니다. 🔺 조회 기간 상한 최대 90일을 제안합니다.

### GET /children/:childId/stats/missions — 미션별 이행률

`series_id` 기준으로 묶은 미션별 이행률. "OO 미션 이행률이 낮아요" 인사이트 생성용입니다.

**Query**: `from` (date, Y), `to` (date, Y)

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "seriesId": "3a7b...",
      "title": "양치하기",
      "completionRate": 42.9,
      "doneCount": 9,
      "totalCount": 21
    }
  ],
  "error": null
}
```

- 이름이 바뀐 미션은 `series_id`가 유지되므로 같은 행으로 집계됩니다. `title`은 **가장 최근 값**을 씁니다.

---

## 12. 캐릭터

> 🚧 **획득 시나리오·`rarity`/`weight` 사용 여부는 회의 대기.** 조회 API만 초안을 잡았습니다.
>
> ✅ **확정 — 진화 규칙 (int 고정).** 캐릭터는 **미션(루틴) N개 수행 후 진화**하는 방식이며, 진화 임계값 N은 **`int` 상수로 고정**합니다(ERD 5-4). 확률·실수 기반이 아닌 누적 미션 완료 수 기준입니다.

### GET /characters — 캐릭터 도감 조회

전체 캐릭터 마스터 목록.

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "characterId": 1,
      "code": "PENGUIN_01",
      "name": "펭구",
      "rarity": "COMMON",
      "assets": {
        "thumbnail": "characters/penguin_01/thumb.png",
        "idle": "characters/penguin_01/idle.png"
      }
    }
  ],
  "error": null
}
```

- `assets`는 **S3 key만 반환**하고 URL은 클라이언트가 런타임에 조립합니다.
- `weight`(획득 가중치)는 서버 내부용이므로 응답에 노출하지 않습니다.
- 🔺 `assets`의 키 구성(thumbnail, idle 등)은 추론. 디자인 산출물이 나와야 확정됩니다.

### GET /children/:childId/characters — 보유 캐릭터 조회

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "childCharacterId": 77,
      "characterId": 1,
      "code": "PENGUIN_01",
      "name": "펭구",
      "level": 3,
      "exp": 120,
      "acquiredAt": "2026-08-20T10:12:00Z"
    }
  ],
  "error": null
}
```

- 진화 규칙은 **미션 N개 수행 시 진화**(임계값 N은 `int` 상수 고정, ERD 5-4). 경험치 획득 시점(루틴 완료 시 서버 계산 vs sync 시점)만 구현 단계에서 확정하면 됩니다.

---

## 13. 커뮤니티

> ❌ **구현 제외.** 커뮤니티 도메인(게시글·댓글·좋아요)은 **이번 구현 범위에서 제외**하기로 결정했습니다(ERD 5-5·5-6). ERD 테이블·스키마(`POSTS`/`COMMENTS`/`POST_LIKES`)는 향후 확장을 위해 정의만 유지하되, 아래 엔드포인트는 **실제 개발하지 않습니다.** 이에 따라 대댓글 삭제 정책, 카운터 갱신 방식, 관리자 신고 처리 등 관련 미확정 항목도 함께 종료합니다.
>
> _아래 명세는 향후 참고용 초안으로만 남겨둡니다._

### GET /posts — 게시글 목록

**Query**: `page` (number, N, 기본 0), `size` (number, N, 기본 20)

**Response 200**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "postId": 500,
        "title": "루틴 정착 팁 공유해요",
        "author": { "userId": 1, "name": "김보호" },
        "likeCount": 12,
        "commentCount": 4,
        "createdAt": "2026-08-30T11:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  },
  "error": null
}
```

- 목록에는 `content` 본문을 싣지 않습니다.

### POST /posts — 게시글 작성

**Request**: `title` (string, Y), `content` (string, Y)

**Response 201**: 게시글 단건

### GET /posts/:postId — 게시글 상세

**Response 200**

```json
{
  "success": true,
  "data": {
    "postId": 500,
    "title": "루틴 정착 팁 공유해요",
    "content": "본문 내용",
    "author": { "userId": 1, "name": "김보호" },
    "likeCount": 12,
    "commentCount": 4,
    "isLiked": true,
    "isMine": true,
    "createdAt": "2026-08-30T11:00:00Z"
  },
  "error": null
}
```

- 🔺 `isLiked`·`isMine`은 추론 항목. 없으면 앱이 좋아요 버튼 상태와 수정·삭제 버튼 노출을 판단할 수 없어 추가했습니다.

### PATCH /posts/:postId — 게시글 수정

**Request**: `title`, `content` (선택, 최소 1개) — **작성자 본인만**

**Response 200**: 게시글 단건

### DELETE /posts/:postId — 게시글 삭제

**Response 204** — soft delete. 유저(본인) 또는 관리자.

> ❓ **미정**: 관리자 신고 처리 시나리오. 관리자 인증 방식(별도 role? 별도 어드민 API?)도 정해진 바가 없습니다.

### POST /posts/:postId/likes — 좋아요 등록

**Response 201**

```json
{
  "success": true,
  "data": { "postId": 500, "likeCount": 13, "isLiked": true },
  "error": null
}
```

- `unique(post_id, user_id)`로 중복을 막습니다. 중복 요청 시 `LIKE_ALREADY_EXISTS` 409.

### DELETE /posts/:postId/likes — 좋아요 취소

**Response 200**: 등록과 동일한 형태 (`isLiked: false`)

### GET /posts/:postId/comments — 댓글 목록

`parent_comment_id` 기준으로 트리를 구성해 반환합니다.

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "commentId": 800,
      "author": { "userId": 1, "name": "김보호" },
      "content": "좋은 팁 감사합니다",
      "isMine": false,
      "isDeleted": false,
      "createdAt": "2026-08-30T12:00:00Z",
      "replies": [
        {
          "commentId": 801,
          "author": { "userId": 2, "name": "이보호" },
          "content": "저도 해볼게요",
          "isMine": true,
          "isDeleted": false,
          "createdAt": "2026-08-30T12:10:00Z"
        }
      ]
    }
  ],
  "error": null
}
```

- 대댓글은 1단계까지만 허용합니다. 대댓글에 답글을 달면 `COMMENT_DEPTH_EXCEEDED` 400.
- 🔺 `isDeleted`는 삭제 정책이 정해져야 의미가 생깁니다. 대댓글이 달린 댓글을 지울 때 "삭제된 댓글입니다"로 남길지 통째로 감출지에 따라 필요 여부가 갈립니다.

### POST /posts/:postId/comments — 댓글 작성

**Request**: `content` (string, Y), `parentCommentId` (number, N — 있으면 대댓글)

**Response 201**: 댓글 단건

### PATCH /comments/:commentId — 댓글 수정

**Request**: `content` (string, Y) — **작성자 본인만**

**Response 200**: 댓글 단건

### DELETE /comments/:commentId — 댓글 삭제

**Response 204** — soft delete. 유저(본인) 또는 관리자.

---

## 14. 온보딩 & 페어링 호출 순서

```
0. 앱 → 서버   온보딩 1차. POST /api/v1/auth/signup { email, password }
                → accessUuid 발급, 가입과 동시에 로그인 상태
                → user.name 이 null 이므로 앱은 성명 입력 화면으로 분기
                  PATCH /api/v1/users/me 로 보호자 성명 입력

1. 앱 → 서버   온보딩 2차. POST /api/v1/children
                POST /api/v1/devices/pairing { childId, nickname }
                → pairingCode + deviceId 수신, PENDING 행 생성
                ※ N기기: 자녀당 PENDING 행이 여러 개일 수 있어
                  deviceId로 이번에 만든 행을 특정 (이후 상태 폴링에 사용)

2. 앱 → 기기   온보딩 3차. (핫스팟) 와이파이 SSID/PW + pairingCode
                ※ 단방향, 기기는 응답만

3. 기기 → 서버 POST /device-api/v1/claim { pairingCode, deviceUid, firmware }
                → 서버가 pairingCode로 PENDING 행 조회
                → device_uid, paired_at 채우고 ACTIVE
                → 서버가 device_access_uuid 를 새로 만들어 저장
                → 응답으로 deviceAccessUuid 반환 (기기가 영속 저장 필수)

4. 앱 → 서버   GET /api/v1/devices/{deviceId} 폴링
                → status가 PENDING → ACTIVE로 바뀌면 온보딩 완료
                ※ 타임아웃 10분 확정. 폴링 주기는 미확정 (15장 #5)

5. 기기 → 서버 X-Device-Uuid: <deviceAccessUuid> 로 POST /device-api/v1/sync
                DEVICE_UNAUTHORIZED 수신 시
                → 자체 복구 경로 없음. 재페어링을 안내
```

> **앱의 모든 요청**은 0번에서 받은 `accessUuid` 를 `X-Access-Uuid` 헤더에 담습니다. 만료가 없으므로 갱신 호출이 따로 없습니다.

## 15. 확정 필요 항목

| # | 항목 | 영향 | 결정 시한 |
|---|---|---|---|
| 3 | `pairingCode` **자릿수 · 문자 구성** | 페어링 코드 생성기 구현 자체 | Phase 2 |
| 4 | 회원 탈퇴 시 cascade 정책 | `DELETE /users/me` 구현 자체 | Phase 2 |
| 5 | 페어링 폴링 **주기** (몇 초마다 호출할지) | 앱·서버 합의 사항. **타임아웃은 10분으로 확정** | Phase 2 |
| 6 | 만료 PENDING 행 **정리 배치 주기** | 페어링 정리 | Phase 2 |
| 7 | 조회 기간 상한 — calendar `from`~`to`(31일 제안), `RANGE` 반복 기간 길이, stats(90일 제안) | 루틴·대시보드 | Phase 3 |
| 8 | sync 부분 실패 처리, `dates` 최대 길이 | 디바이스 sync | Phase 4 |
| 9 | 인사이트 종류와 생성 규칙 | 대시보드 | Phase 5 |
| 10 | 캐릭터 획득 시나리오, `rarity`/`weight` 사용 여부 (진화 규칙은 int 고정으로 확정) | 캐릭터 API | 회의 |

### 종료된 항목

아래 항목은 결정이 끝나 확정 필요 목록에서 제외되었습니다.

| 항목 | 결정 |
|---|---|
| **기기 API envelope** | ✅ **앱과 동일한 공통 envelope 적용.** 인증 실패 코드만 `DEVICE_UNAUTHORIZED` 로 구분 (2026-09-09) |
| **로깅에서 가릴 항목** | ✅ **확정 (2026-09-09).** UUID 계열(`accessUuid`·`deviceAccessUuid`)은 **앞뒤 3글자만 남기고** 가운데를 가림. `password`·`pairingCode` 는 **통째로 가림** |
| **비밀번호 해시 방식** | ✅ **bcrypt** (`BCryptPasswordEncoder`, 기본 강도 10). Spring Security 에 이미 들어 있어 의존성이 늘지 않음 (2026-09-09) |
| **비밀번호 정책** | ✅ **최소 8자, 문자 조합 규칙 없음.** 조합 규칙은 사용자가 기억하기 어려운 비밀번호를 만들게 해 오히려 재사용을 부른다 (2026-09-09) |
| **JWT 서명 알고리즘 · 키 관리** | ✅ **소멸.** JWT 를 쓰지 않기로 해 결정할 대상이 없어짐 (2026-09-09) |
| **access/refresh 토큰 만료 · rotation** | ✅ **소멸.** 만료 없는 UUID 방식으로 전환 (2026-09-09) |
| **기기 `token` · `secret` 해시 방식** | ✅ **소멸.** 기기 토큰 체계 삭제 (2026-09-09) |
| **지연 생성 동시성 방어** | ✅ **소멸.** 빅루틴 즉시 생성으로 전환, 조회 시점 생성이 없어짐 (2026-09-09) |
| 로그인 방식 | ✅ **이메일 · 비밀번호.** 소셜 로그인 삭제. 비밀번호 찾기 · 이메일 인증 **구현 안 함** (2026-09-09) |
| 단일 루틴 / 복합 루틴 구분 | ✅ **서버는 구분하지 않음.** 앱이 `smallRoutines` 길이로 판단 (2026-09-09) |
| 루틴 반복 생성 | ✅ **빅루틴에서 `RANGE` / `WEEKLY` / `DATES` 3종, 배타 선택, 즉시 생성** (2026-09-09) |
| `DATES` 날짜 개수 상한 | ✅ **12개** (2026-09-09) |
| 빅루틴 수정 · 삭제 전파 범위 | ✅ **같은 `series_id` 전체, 단 오늘 이전 날짜 제외.** 수정 기본 `series`, 삭제 기본 `single` (2026-09-09) |
| 템플릿의 역할 | ✅ **저장해둔 양식.** 수정해도 이미 만든 빅루틴에 전파되지 않음 (2026-09-09) |
| 날짜별 루틴 조회 API | ✅ **삭제.** 캘린더가 한 달치 상세를 통째로 반환 (2026-09-09) |
| 타임존 정책 | ✅ 표준 UTC 방식 확정 (서버 UTC 저장, 앱·기기 KST 변환) |
| `relationship` enum 값 목록 | ✅ **`PARENT` · `ADMIN` 2개** (임시 확정) |
| 캐릭터 진화 규칙 | ✅ 미션 N개 수행 시 진화, 임계값 N을 int 상수로 고정 |
| 커뮤니티 (댓글 삭제 정책·카운터 갱신·관리자 신고) | ❌ 구현 제외 |
| 보호자당 최대 자녀 수 | ✅ **10명** |
| 자녀당 최대 기기 수 | ✅ **10대** |
| 페어링 폴링 **타임아웃** | ✅ **10분.** 페어링 코드 만료(10분)와 같은 값이라 앱이 포기하는 시점과 서버에서 코드가 죽는 시점이 일치한다 |
