# 예소 API 명세서

> 출처: Notion `예소 개발자용 > API 기본 명세서` (엔드포인트 DB) + `API Req/Res 명세 초안`
> 총 **48개** 엔드포인트 · 앱용 `/api/v1/**` 45개, 기기용 `/device-api/v1/**` 3개
> — 2026-09-04 Notion 엔드포인트 DB 원본(48행)과 대조해 확인했습니다. 이전 표기 "46개(앱 43)"는 오기였습니다.
> 커뮤니티 11개는 구현 제외이므로 **실제 구현 대상은 37개**입니다.
> 최종 수정: 2026-09-04 — 날짜 동기화만 수행. **ROADMAP 의 테스트 환경(T-1 ~ T-5) 확정은 엔드포인트·req/res·에러코드에 영향 없음.**
> (직전 수정: 2026-08-30)

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
10. [고정 루틴 템플릿](#10-고정-루틴-템플릿)
11. [대시보드](#11-대시보드)
12. [캐릭터](#12-캐릭터)
13. [커뮤니티](#13-커뮤니티)
14. [온보딩 & 페어링 호출 순서](#14-온보딩--페어링-호출-순서)
15. [확정 필요 항목](#15-확정-필요-항목)

---

## 1. 공통 규약

### 1-1. Base URL · 인증

| 구분 | Base URL | 인증 방식 | 헤더 |
|---|---|---|---|
| 앱 (보호자) | `/api/v1` | JWT (access token) | `Authorization: Bearer ...` |
| 기기 | `/device-api/v1` | opaque token (claim 시 발급, 시간 만료 없음) | `Authorization: Bearer ...` |

인증 불필요 엔드포인트: `POST /auth/social-login`, `POST /auth/refresh`, `POST /device-api/v1/claim`, `POST /device-api/v1/token/refresh`

앱과 기기는 인증 방식이 완전히 달라 **SecurityFilterChain을 2개로 분리**해야 합니다. `/device-api/**`에는 JWT 필터가 걸리지 않아야 합니다.

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

> ❓ **논의 필요 — 기기 API envelope.** 기기 펌웨어의 JSON 파서 부담을 줄이려면 `/device-api/**`만 envelope 없이 flat 응답으로 가는 선택지도 있습니다. 대신 앱·기기 응답 처리 코드가 갈라집니다.

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
| `AUTH_INVALID_PROVIDER` | 400 | 지원하지 않는 로그인 방식입니다. | social-login |
| `AUTH_INVALID_ID_TOKEN` | 401 | 소셜 로그인에 실패했습니다. | social-login |
| `AUTH_TOKEN_EXPIRED` | 401 | 로그인이 만료되었습니다. 다시 로그인해 주세요. | 전 구간 |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | 다시 로그인해 주세요. | refresh |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. | users/me |
| `USER_ALREADY_WITHDRAWN` | 409 | 이미 탈퇴한 계정입니다. | users/me |

### 3-3. 자녀

| code | HTTP | message |
|---|---|---|
| `CHILD_NOT_FOUND` | 404 | 자녀를 찾을 수 없습니다. |
| `CHILD_FORBIDDEN` | 403 | 해당 자녀에 대한 권한이 없습니다. |
| `CHILD_LIMIT_EXCEEDED` | 409 | 등록 가능한 자녀 수를 초과했습니다. |

### 3-4. 기기 · 페어링

| code | HTTP | message | 비고 |
|---|---|---|---|
| `DEVICE_NOT_FOUND` | 404 | 기기를 찾을 수 없습니다. | |
| `DEVICE_FORBIDDEN` | 403 | 해당 기기에 대한 권한이 없습니다. | |
| `DEVICE_LIMIT_EXCEEDED` | 409 | 자녀당 등록 가능한 기기 수를 초과했습니다. | N기기 정책 확정 후 임계값 결정 |
| `PAIRING_CODE_NOT_FOUND` | 404 | 유효하지 않은 페어링 코드입니다. | claim |
| `PAIRING_CODE_EXPIRED` | 410 | 페어링 코드가 만료되었습니다. | 발급 후 10분 |
| `PAIRING_CODE_ALREADY_USED` | 409 | 이미 사용된 페어링 코드입니다. | 일회용 |
| `DEVICE_UID_ALREADY_PAIRED` | 409 | 이미 다른 계정에 연결된 기기입니다. | `unique(device_uid)` 위반 |
| `DEVICE_TOKEN_INVALID` | 401 | 기기 인증에 실패했습니다. | 기기 → token/refresh 유도 |
| `DEVICE_SECRET_INVALID` | 401 | 기기 인증 정보가 올바르지 않습니다. | 재페어링 필요 |

### 3-5. 루틴

| code | HTTP | message |
|---|---|---|
| `BIG_ROUTINE_NOT_FOUND` | 404 | 루틴을 찾을 수 없습니다. |
| `SMALL_ROUTINE_NOT_FOUND` | 404 | 할 일을 찾을 수 없습니다. |
| `ROUTINE_TEMPLATE_NOT_FOUND` | 404 | 고정 루틴을 찾을 수 없습니다. |
| `ROUTINE_INVALID_TIME_RANGE` | 400 | 종료 시각이 시작 시각보다 빠를 수 없습니다. |
| `ROUTINE_INVALID_DATE_RANGE` | 400 | 종료일이 시작일보다 빠를 수 없습니다. |
| `ROUTINE_DATE_RANGE_TOO_LONG` | 400 | 한 번에 등록할 수 있는 기간을 초과했습니다. |
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

### POST /auth/social-login — 소셜 로그인

구글·카카오 소셜 로그인. 최초 로그인 시 회원가입 처리 후 JWT 발급.

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| provider | enum | Y | GOOGLE, KAKAO |
| idToken | string | Y | 소셜 SDK가 발급한 ID 토큰 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "isFirstLogin": true,
    "user": { "userId": 1, "name": null, "email": "parent@example.com" }
  },
  "error": null
}
```

- `isFirstLogin`이 true면 앱은 온보딩 1차(보호자 성명 입력)로 분기합니다.
- 🔺 토큰 만료 시간 미정. access 30분 / refresh 14일 정도를 제안하나 팀 확정 필요.

**Error**: `AUTH_INVALID_PROVIDER` 400, `AUTH_INVALID_ID_TOKEN` 401

### POST /auth/refresh — 토큰 재발급

**Request**: `refreshToken` (string, Y)

**Response 200**

```json
{
  "success": true,
  "data": { "accessToken": "eyJhbGciOi...", "refreshToken": "eyJhbGciOi..." },
  "error": null
}
```

> ❓ **확정 필요**: refresh token rotation 여부. 재발급 시 refreshToken도 새로 주고 기존 것을 무효화할지, accessToken만 줄지.

**Error**: `AUTH_REFRESH_TOKEN_INVALID` 401

### POST /auth/logout — 로그아웃

리프레시 토큰 무효화. 바디 없음, Authorization 헤더로 식별.

**Response 204**

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
    "provider": "KAKAO",
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
| relationship | enum | Y | enum으로 고정 (값 목록은 회의에서 확정 예정) |

> ✅ **확정 — enum 사용.** `relationship`은 **enum으로 고정**합니다(ERD 5-3). 다만 **구체적인 enum 값 목록은 회의에서 확정 예정**이므로, 예시(MOTHER / FATHER / GRANDPARENT / TEACHER / ETC 등)는 잠정값입니다. 값 확정 후 이 표와 아래 예시를 갱신해야 합니다.

**Response 201**

```json
{
  "success": true,
  "data": {
    "childId": 10,
    "name": "김아이",
    "birthDate": "2018-03-02",
    "relationship": "MOTHER",
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
      "relationship": "MOTHER",
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
    "relationship": "MOTHER",
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

> ⚠️ 이 구간은 앱과 인증 방식이 완전히 다릅니다. SecurityFilterChain을 분리해 `/device-api/**`에는 JWT 필터가 걸리지 않도록 해야 합니다.

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
    "token": "dev_a1b2c3d4...",
    "secret": "sec_e5f6g7h8...",
    "serverTime": "2026-09-01T08:35:12Z"
  },
  "error": null
}
```

**서버 처리 순서**

1. `pairingCode`로 `status = 'PENDING'`인 행 조회 (`unique(pairing_code) where status = 'PENDING'` 덕분에 단일 행 특정)
2. 만료 시각 검증 (발급 후 10분)
3. `device_uid`, `token_hash`, `secret_hash`, `paired_at` 채우고 `status = 'ACTIVE'`
4. `pairing_code`를 NULL 처리 (일회용)
5. 평문 `token`, `secret` 반환 — **이 응답이 평문을 볼 수 있는 유일한 시점입니다.** 기기가 반드시 영속 저장해야 합니다.

- 🔺 `serverTime`은 추론 항목. claim 직후 RTC를 맞춰두면 첫 sync 전까지의 시각 오차를 줄일 수 있어 추가했습니다.

**Error**: `PAIRING_CODE_NOT_FOUND` 404, `PAIRING_CODE_EXPIRED` 410, `PAIRING_CODE_ALREADY_USED` 409, `DEVICE_UID_ALREADY_PAIRED` 409

### POST /device-api/v1/token/refresh — 기기 토큰 재발급

재페어링 없이 기기가 자체 복구하는 유일한 경로입니다. **인증 헤더 없음** (secret이 인증 수단).

**Request**: `deviceUid` (string, Y), `secret` (string, Y)

**Response 200**

```json
{
  "success": true,
  "data": { "token": "dev_newtoken...", "serverTime": "2026-09-01T08:35:12Z" },
  "error": null
}
```

- 토큰은 시간 만료가 없습니다. 유출·사고 시 재발급용입니다.
- 재발급 시 `token_hash`를 덮어써 기존 토큰이 자동 무효화됩니다.
- 🔺 `secret` 회전 여부 미정. 회전시키면 보안상 낫지만 기기가 새 secret 저장에 실패하면 복구 불능이 됩니다. **회전하지 않는 쪽을 권장합니다.**

**Error**: `DEVICE_SECRET_INVALID` 401, `DEVICE_NOT_FOUND` 404

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
- **고정 루틴 지연 생성 트리거 지점.** `dates`에 담긴 날짜에 템플릿 기반 빅루틴이 없으면 이 시점에 생성합니다.
- UPDATE 기반이라 **멱등성이 보장**됩니다. 네트워크 실패로 기기가 같은 `completions`를 재전송해도 안전합니다.
- 🔺 `accepted` 카운트와 부분 실패 처리 방식은 추론. 일부 `smallRoutineId`가 이미 삭제된 경우 전체를 실패시킬지 무시할지 정해야 합니다. **무시하고 넘어가는 쪽을 권장** — 기기는 재시도 외에 할 수 있는 일이 없습니다.
- 🔺 `dates` 배열 최대 길이 제한 필요. 3일을 제안합니다.

**Error**: `DEVICE_TOKEN_INVALID` 401 (기기는 이 코드를 받으면 `token/refresh`로 자체 복구를 시도해야 합니다)

---

## 9. 루틴

### POST /children/:childId/big-routines — 빅루틴 생성

단일 날짜 또는 기간 배치 생성. 기간 지정 시 날짜별 행을 각각 만들고 **동일 `series_id`를 부여**합니다.

**Request**

| key | 타입 | 필수 | 설명 |
|---|---|---|---|
| title | string | Y | 빅루틴 제목 |
| startTime | time | Y | HH:mm |
| endTime | time | Y | HH:mm. startTime보다 뒤여야 함 |
| startDate | date | Y | 시작일 |
| endDate | date | N | 미지정 시 startDate 하루만 생성 |
| smallRoutines | array | N | 내부 할 일 목록 |

```json
{
  "title": "아침 준비",
  "startTime": "07:30",
  "endTime": "08:30",
  "startDate": "2026-09-01",
  "endDate": "2026-09-07",
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
    "createdDates": ["2026-09-01", "2026-09-02", "2026-09-03"],
    "createdCount": 3
  },
  "error": null
}
```

- `seriesId`, `createdDates` 반환은 명세에 명시된 항목입니다.
- 날짜 범위로 만든 루틴은 **템플릿을 생성하지 않습니다.** 고정 반복이 필요하면 `routine-templates`를 씁니다.

**Error**: `ROUTINE_INVALID_TIME_RANGE`, `ROUTINE_INVALID_DATE_RANGE`, `ROUTINE_DATE_RANGE_TOO_LONG` (모두 400)

### GET /children/:childId/routines — 날짜별 루틴 조회

**Query**: `date` (date, Y)

**Response 200**

```json
{
  "success": true,
  "data": {
    "date": "2026-09-01",
    "bigRoutines": [
      {
        "bigRoutineId": 300,
        "seriesId": "9f1c2e40-...",
        "templateId": 12,
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
  },
  "error": null
}
```

> ⚡ **고정 루틴 지연 생성 트리거 지점.** 조회 시점에 해당 날짜의 템플릿 기반 빅루틴이 없으면 생성한 뒤 응답합니다. 동시 요청 시 중복 생성을 막을 장치가 필요합니다 — `unique(template_id, routine_date) where deleted_at is null` 제약 + upsert를 제안합니다.

- `templateId`가 null이면 템플릿 없이 직접 만든 루틴입니다.
- `status`: `PENDING` | `DONE`

### GET /children/:childId/calendar — 캘린더 조회

루틴 등록 메인창 캘린더용. 날짜별 요약만 반환합니다.

**Query**: `from` (date, Y), `to` (date, Y)

**Response 200**

```json
{
  "success": true,
  "data": [
    { "date": "2026-09-01", "totalCount": 8, "doneCount": 6, "completionRate": 75.0 },
    { "date": "2026-09-02", "totalCount": 8, "doneCount": 0, "completionRate": 0.0 }
  ],
  "error": null
}
```

- 이 엔드포인트도 **지연 생성 트리거 지점**입니다. 조회 기간 전체에 대해 생성이 일어나므로 `from`~`to` 최대 범위 제한이 필요합니다. 🔺 31일을 제안합니다.
- 🔺 응답 필드 구성은 추론. 캘린더에 점만 찍을지 이행률까지 보여줄지에 따라 달라집니다.

### PATCH /big-routines/:bigRoutineId — 빅루틴 수정

**Request**: `title`, `startTime`, `endTime` (모두 선택, 최소 1개)

**Response 200**: 빅루틴 단건

- **해당 날짜만** 수정됩니다. `series_id`는 유지되고 다른 날짜에는 영향이 없습니다.

### DELETE /big-routines/:bigRoutineId — 빅루틴 삭제

**Query**: `scope` — `single` (해당 날짜만) | `series` (같은 series 전체)

**Response 204**

- soft delete. 이행률 통계 보존을 위해 물리 삭제하지 않습니다.
- 🔺 `scope` 기본값은 `single`을 제안합니다. 실수로 전체가 지워지는 것보다 안전합니다.

### POST /big-routines/:bigRoutineId/small-routines — 스몰루틴 추가

**Request**: `title` (string, Y), `order` (number, N)

**Response 201**

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

## 10. 고정 루틴 템플릿

### POST /children/:childId/routine-templates — 템플릿 생성

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
    ],
    "isActive": true
  },
  "error": null
}
```

- 템플릿의 `smallRoutines`는 ERD상 `jsonb` 컬럼입니다. 별도 테이블이 아니므로 개별 id가 없습니다.

> ❌ **결정 — 반복 요일 미지원 (개발 제외).** 요일별 반복("평일만" 등)은 구현 범위에서 제외하기로 했습니다(ERD 5-1). 템플릿은 **매일 반복만** 지원하며, 요일 지정 필드는 추가하지 않습니다.

### GET /children/:childId/routine-templates — 템플릿 목록

활성화된(`is_active = true`) 템플릿만 반환합니다.

**Response 200**: 템플릿 배열

### PATCH /routine-templates/:templateId — 템플릿 수정

**Request**: `title`, `startTime`, `endTime`, `smallRoutines` (모두 선택)

**Response 200**: 템플릿 단건

> ⚠️ **이후 생성분부터 적용됩니다.** 이미 생성된 빅루틴은 변경되지 않습니다. 앱에서 사용자에게 안내해야 혼란이 없습니다.

### DELETE /routine-templates/:templateId — 템플릿 비활성화

**Response 204**

- `is_active = false` 처리로 **자동 생성만 중단**됩니다. 기존에 생성된 빅루틴은 유지됩니다.

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
0. 앱 → 서버   온보딩 1차. POST /api/v1/auth/social-login
                → JWT 발급 및 유저 인증 (isFirstLogin으로 분기)
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
                → device_uid, token_hash, secret_hash, paired_at 채우고 ACTIVE
                → 응답으로 token, secret 반환 (평문을 볼 수 있는 유일한 시점)

4. 앱 → 서버   GET /api/v1/devices/{deviceId} 폴링
                → status가 PENDING → ACTIVE로 바뀌면 온보딩 완료
                ※ 2초 간격 / 최대 3분 제안, 타임아웃 시 재시도 화면

5. 기기 → 서버 Authorization: Bearer <token> 으로 POST /device-api/v1/sync
                DEVICE_TOKEN_INVALID 수신 시
                → POST /device-api/v1/token/refresh 로 자체 복구
```

## 15. 확정 필요 항목

| # | 항목 | 영향 | 결정 시한 |
|---|---|---|---|
| 1 | 기기 API에 공통 envelope 적용 여부 | 디바이스 API 3종, 기기 펌웨어 | Phase 1 이전 |
| 2 | access/refresh 토큰 만료 시간, rotation 여부 | 인증 | Phase 1 |
| 11 | **JWT 서명 알고리즘과 비밀키 관리 방식** (HS256 대칭키 / RS256 비대칭키, 키를 환경변수·시크릿 어디에 둘지) | JWT 검증 필터 구현 자체 | Phase 1 (1-4 이전) |
| 12 | **요청·응답 로깅에서 가릴 항목** (`idToken`, `accessToken`, `refreshToken`, 기기 `token`·`secret`, `pairingCode`) | 보안 설정의 로깅 필터 | Phase 1 (1-2 마무리 전) |
| 3 | 자녀당 최대 기기 수 | `DEVICE_LIMIT_EXCEEDED` 임계값 | 회의 |
| 4 | 회원 탈퇴 시 cascade 정책 | `DELETE /users/me` 구현 자체 | Phase 2 |
| 5 | 페어링 폴링 주기·타임아웃 | 앱·서버 합의 사항 | Phase 2 |
| 6 | `relationship` enum **값 목록** 확정 (enum 사용은 확정) | 자녀 등록 | 회의 |
| 7 | 조회 기간 상한 (calendar 31일, stats 90일 제안) | 루틴·대시보드 | Phase 3 |
| 8 | sync 부분 실패 처리, `dates` 최대 길이 | 디바이스 sync | Phase 4 |
| 9 | 인사이트 종류와 생성 규칙 | 대시보드 | Phase 5 |
| 10 | 캐릭터 획득 시나리오, `rarity`/`weight` 사용 여부 (진화 규칙은 int 고정으로 확정) | 캐릭터 API | 회의 |

### 종료된 항목

아래 항목은 결정이 끝나 확정 필요 목록에서 제외되었습니다.

| 항목 | 결정 |
|---|---|
| 타임존 정책 | ✅ 표준 UTC 방식 확정 (서버 UTC 저장, 앱·기기 KST 변환) |
| `relationship` enum 사용 | ✅ enum 고정 확정 (값 목록만 회의 대기) |
| 캐릭터 진화 규칙 | ✅ 미션 N개 수행 시 진화, 임계값 N을 int 상수로 고정 |
| 템플릿 반복 요일 | ❌ 개발 제외 (매일 반복만 지원) |
| 커뮤니티 (댓글 삭제 정책·카운터 갱신·관리자 신고) | ❌ 구현 제외 |
