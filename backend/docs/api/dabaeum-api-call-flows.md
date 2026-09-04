# 다배움 API 호출 순서 정의서 (중간점검 항목별 데이터 흐름)

> **대상**: 프론트엔드 개발팀
> **기준**: `docs/api/dabaeum-api-v1.yaml` (OpenAPI 3.1, 63 operations) + 백엔드 구현 코드
> **작성일**: 2026-08-12
> **목적**: 중간점검 체크리스트(B-1~F-3) 항목별로 화면에서 호출해야 하는 API의 **순서**와 **호출 간 전달되는 데이터(ID 체인)** 를 정의한다. 개별 API의 상세 스키마는 OpenAPI 명세를 따른다.

---

## 0. 공통 규약

| 항목 | 내용 |
|---|---|
| Base URL | `/api/v1` |
| 인증 | `Authorization: Bearer {JWT}` (로그인 응답으로 획득). 공개 API는 명세에 `security: []` 표기 |
| 응답 envelope | 단건 `{ "data": {...} }`, 목록 `{ "data": [...], "page": {...} }` |
| 페이지 파라미터 | `page`, `size`, `sort` (프론트 `PageQuery` 타입) |
| Idempotency-Key | 상태 변경 POST/PUT/PATCH에 UUID 헤더 첨부 (프론트 `client.ts`의 `newIdempotencyKey()`가 자동 처리). QR 토큰 발급·출결 기록·이수 확정 등 서버가 자연 멱등 처리하는 API는 명세상 생략 가능하나, 첨부해도 무해 |
| 에러 | `ApiError` 스키마. 409(상태 충돌·중복), 422(비즈니스 규칙 위반), 401/403(인증·권한) |

**핵심 ID 체인 (데이터 흐름의 뼈대)**

```
institutionId → courseId → sessionId ─┐
                    │                 ├→ attendanceId
                    └→ enrollmentId ──┤
                              │       └→ (attendance-summary)
                              └→ completionId → credentialId → credentialNo / vcHash
```

앞 단계 응답의 `data.id`가 다음 단계의 path parameter가 된다. 프론트는 이 체인을 라우팅 파라미터/쿼리 캐시 키로 유지하면 된다 (`queries.ts`의 queryKey 구조와 일치).

---

## 1. [B-1·F-3] 시민 E2E: 로그인→강좌조회→신청→QR출결→이수→VC발급→검증

중간점검 시연의 메인 시나리오. **학습자(APP)** 와 **기관 관리자(ADMIN_WEB)** 두 화면이 번갈아 호출한다.

### 1-1. 학습자: 로그인 ~ 수강신청

| # | 화면 | Method / Path | operationId | 전달 데이터 |
|---|---|---|---|---|
| 1 | 로그인 | `POST /auth/login` | loginLocalAccount | email/password → **JWT 획득** (실증 단계에선 다대구 SSO로 대체 예정) |
| 2 | 앱 진입 | `GET /auth/session` | getAuthSession | roles·userId 확인 → 화면 분기 (LEARNER/INSTITUTION_ADMIN/PLATFORM_ADMIN) |
| 3 | 강좌 목록 | `GET /courses?page&size` | listCourses | 모집중(`RECRUITING`) 강좌 노출 → **courseId** |
| 4 | 강좌 상세 | `GET /courses/{courseId}` | getCourse | 정원·기간·학점 표시 |
| 5 | 강좌 상세 | `GET /courses/{courseId}/sessions` | listCourseSessions | 회차 일정 표시 → **sessionId** (출결 단계에서 사용) |
| 6 | 신청 버튼 | `POST /courses/{courseId}/enrollments` | createEnrollment | → **enrollmentId**, status=`APPLIED` |

### 1-2. 기관 관리자: 승인

| # | 화면 | Method / Path | operationId | 전달 데이터 |
|---|---|---|---|---|
| 7 | 신청 관리 | `GET /courses/{courseId}/enrollments` | listCourseEnrollments | APPLIED 목록 조회 |
| 8 | 승인 | `POST /enrollments/{enrollmentId}/approve` | approveEnrollment | status → `APPROVED` (거절 시 `/reject` + reason) |

### 1-3. 출결 (수업 당일) — 동적 QR

| # | 화면 | Method / Path | operationId | 전달 데이터 |
|---|---|---|---|---|
| 9 | 관리자: QR 표시 화면 | `POST /sessions/{sessionId}/qr-token` | issueAttendanceQrToken | → `{token, expiresAt}` **TTL 30초**. 화면은 만료 전 반복 재호출(권장 25초 주기)해 QR 갱신. Idempotency-Key 불필요, 재발급 시 이전 토큰 대체 |
| 10 | 학습자: QR 스캔 | `POST /sessions/{sessionId}/attendance` | recordAttendance | body: `{enrollmentId, attendanceMethod:"QR", status:"PRESENT", source:"APP", qrToken}` → **attendanceId**. 만료/위조 토큰은 422, 중복 스캔은 409 |
| 11 | 관리자: 출결 현황 | `GET /sessions/{sessionId}/attendance` | listSessionAttendance | 회차별 출결 목록 실시간 갱신 |

### 1-4. 이수 확정

| # | 화면 | Method / Path | operationId | 전달 데이터 |
|---|---|---|---|---|
| 12 | 관리자: 이수 판정 | `GET /enrollments/{enrollmentId}/attendance-summary` | getAttendanceSummary | attendanceRate 등 집계 확인 |
| 13 | 평가 | `POST /enrollments/{enrollmentId}/completion/evaluate` | evaluateCompletion | body: `{attendanceRate, completedMinutes, creditValue?}` → completion status = `ELIGIBLE` 또는 `NOT_COMPLETED` |
| 14 | 확정 | `POST /enrollments/{enrollmentId}/completion/confirm` | confirmCompletion | status → `COMPLETED`, → **completionId** (`GET .../completion`으로도 조회 가능) |

### 1-5. VC 발급 (비동기) → 검증

| # | 화면 | Method / Path | operationId | 전달 데이터 |
|---|---|---|---|---|
| 15 | 관리자: 발급 요청 | `POST /completions/{completionId}/credentials` | issueCredential | **202 Accepted** → **credentialId** (status=`PENDING`). Idempotency-Key 필수 |
| 16 | 발급 상태 polling | `GET /credentials/{credentialId}` | getCredential | `PENDING → ISSUING → ISSUED/FAILED` 될 때까지 polling (권장 2~3초 간격, 블록체인 앵커링 완료 시 ISSUED + vcHash·credentialNo 확정) |
| 17 | 학습자: 학습지갑 | `GET /users/me/credentials` | listCurrentUserCredentials | 인증된 내 수료증 목록 (userId 전달 없음) |
| 18 | 수료증 원문 | `GET /credentials/{credentialId}/document` | downloadCredentialDocument | **VC JWT 원문** (`application/vc+jwt`) — QR/공유용 |
| 19 | 외부검증 (무인증) | `POST /credentials/verify` | verifyCredential | body: `{credentialNo 또는 credentialHash, verificationType:"QR", requesterType:"INDIVIDUAL"}` → result: `VALID/REVOKED/...` |

> **F-3 최종 E2E 시연**은 1~19를 실제 강좌 데이터로 한 번에 수행하는 것. 리허설 시 각 단계 응답의 ID를 다음 단계에 그대로 넘기는지 확인할 것.

---

## 2. [B-2] 상태전이 모델과 전이를 일으키는 API

프론트는 상태값에 따라 버튼 활성화/비활성화를 제어해야 한다. **전이는 반드시 아래 API로만 발생**하며, 잘못된 상태에서 호출하면 409/422가 반환된다.

### Enrollment (수강신청)

```
APPLIED ──approve──→ APPROVED ──withdraw──→ WITHDRAWN
   │ └──reject──→ REJECTED
   └──cancel──→ CANCELLED        (WAITLISTED: 정원 초과 시)
```

| 전이 | API | 권한 |
|---|---|---|
| 신청 생성 (APPLIED) | `POST /courses/{courseId}/enrollments` | 학습자 본인 |
| 대리 신청 | `POST /courses/{courseId}/proxy-enrollments` | 기관 관리자 |
| APPLIED→APPROVED | `POST /enrollments/{id}/approve` | 기관 관리자 |
| APPLIED→REJECTED | `POST /enrollments/{id}/reject` (reason 필수) | 기관 관리자 |
| APPLIED→CANCELLED | `POST /enrollments/{id}/cancel` | 학습자 |
| APPROVED→WITHDRAWN | `POST /enrollments/{id}/withdraw` | 학습자 |

### Completion (이수)

```
PENDING_EVALUATION ──evaluate──→ ELIGIBLE ──confirm──→ COMPLETED
                        └─────────→ NOT_COMPLETED (failureReason)
```

### Credential (수료증)

```
PENDING → ISSUING → ISSUED ──revoke──→ REVOKED
              └→ FAILED      └──reissue──→ (신규 버전 PENDING→…→ISSUED, 구버전 SUPERSEDED)
                             └─(validUntil 경과)→ EXPIRED
```

| 전이 | API | 비고 |
|---|---|---|
| 발급 요청 (PENDING) | `POST /completions/{completionId}/credentials` | 202 + polling |
| ISSUED→REVOKED | `POST /credentials/{id}/revoke` (reason 필수) | 관리자 |
| 재발급 | `POST /credentials/{id}/reissue` (reason 필수) | 새 credential 생성, versionNo+1, 이전 건 SUPERSEDED |

### Course (강좌) — 관리자 사전 준비

```
DRAFT ──publish──→ RECRUITING ──close──→ RECRUITMENT_CLOSED → IN_PROGRESS → COMPLETED
```

`POST /courses/{id}/publish`, `POST /courses/{id}/close`

---

## 3. [B-3] 동적 QR 상세 흐름

QR 토큰은 **HMAC 서명 + nonce + tokenId + sessionId + 발급/만료시각**을 담은 서버 서명 토큰이며 **TTL 30초** (`DefaultAttendanceQrTokenApplicationService.DEFAULT_TTL_SECONDS = 30`).

```
[관리자 QR 화면]                       [서버]                    [학습자 앱]
    │ ①POST /sessions/{id}/qr-token     │                           │
    │──────────────────────────────────→│                           │
    │ ← {token, expiresAt}              │                           │
    │ ②토큰을 QR로 렌더링                │                           │
    │ ③expiresAt-5초 시점에 ① 재호출     │                           │
    │   (이전 토큰은 서버에서 대체됨)      │                           │
    │                                   │      ④QR 카메라 스캔       │
    │                                   │←──────────────────────────│
    │                                   │ ⑤POST /sessions/{id}/attendance
    │                                   │   {enrollmentId, QR, qrToken}
    │                                   │ ⑥서명·만료·nonce·세션ID 검증 │
    │                                   │──── 201 attendance ──────→│
```

**프론트 구현 포인트**

- 관리자 QR 화면: `expiresAt` 기준 카운트다운 표시, 만료 5초 전 자동 재발급. 네트워크 지연 대비 만료 직후 스캔은 422 응답 → "QR이 만료되었습니다. 새 QR을 스캔하세요" 안내.
- 학습자 앱: 스캔 → 즉시 전송. 실패 코드 분기: `422`(만료·위조·세션 불일치), `409`(이미 출석 처리됨 — 성공으로 간주하고 출석 완료 화면 표시 권장), `403`(승인되지 않은 수강생).
- 오프라인/시간오차 대응: 서버 시간이 기준. 클라이언트 시계로 만료 판정하지 말 것.

---

## 4. [B-4·B-5] 출결 보정 흐름

수동 보정은 **사유 필수 + 이력 보존(append-only)** 이 점검 포인트다.

| # | 화면 | Method / Path | 비고 |
|---|---|---|---|
| 1 | 관리자: 회차 출결 목록 | `GET /sessions/{sessionId}/attendance` | 결석/오류 건 식별 |
| 2 | 보정 실행 | `PATCH /attendance/{attendanceId}` | body: `{status, reason}` — reason 없으면 400. 원 기록은 덮어쓰지 않고 보정 이력으로 보존 |
| 3 | 결과 확인 | `GET /attendance/{attendanceId}` | 보정 후 상태 확인 |
| 4 | 이수율 재확인 | `GET /enrollments/{enrollmentId}/attendance-summary` | 보정이 집계에 반영됐는지 확인 |

관리자 직접 출석 입력(QR 미사용)은 `POST /sessions/{sessionId}/attendance`에 `attendanceMethod:"ADMIN", source:"ADMIN_WEB"` (qrToken 금지)로 호출한다.

---

## 5. [C-1·C-2] VC 구조·발급자 공개 정보

외부 검증기관/제3자 화면이 인증 없이 호출하는 공개 API들:

| 용도 | Method / Path | 비고 |
|---|---|---|
| JSON-LD Context | `GET /vc/contexts/lifelong-education/v1` | VC `@context` 해석용 |
| 용어 정의 | `GET /vc/vocabulary/lifelong-education/v1` | |
| 발급기관 공개키 | `GET /vc/issuers/{institutionId}` | VC 서명(Ed25519) 검증용 공개키·Issuer DID |
| 공개 상태 조회 | `GET /vc/status/{credentialNo}` | DB·서명·해시·**Fabric 원장**까지 검증한 최소 상태 반환. `Cache-Control: no-store`. 원장 서비스 불가 시 503 |

**VC 자체 검증 데이터 흐름** (외부 검증자 관점): VC JWT 입수(QR/파일) → JWT 헤더에서 issuer 확인 → `GET /vc/issuers/{institutionId}`로 공개키 획득 → 서명 로컬 검증 → `GET /vc/status/{credentialNo}`로 현재 상태(정정·폐기 반영) 확인.

---

## 6. [C-4] 외부검증 화면 흐름 (무인증)

공개 검증 페이지(모바일 우선)의 호출 순서:

| # | 트리거 | Method / Path | 데이터 |
|---|---|---|---|
| 1 | QR 스캔 또는 번호 입력 | `POST /credentials/verify` | `{credentialNo \| credentialHash, verificationType:"QR"\|"API", requesterType:"INDIVIDUAL"\|"EXTERNAL_ORGANIZATION"}` |
| 2 | 결과 표시 | (응답) | `result`: `VALID` / `INVALID` / `REVOKED` / `SUPERSEDED` / `EXPIRED` / `NOT_FOUND` / `ERROR` — 색상·문구 분기. 개인정보(성명·성적)는 응답에 없음 → 화면에도 노출 금지 |
| 3 | (선택) 원장 상태 추가 확인 | `GET /vc/status/{credentialNo}` | 블록체인 앵커 상태까지 표시할 때 |

- 검증 요청은 **raw VC를 서버로 보내지 않는다** (credentialNo 또는 hash만 전송).
- 모든 검증 시도는 서버가 이력으로 기록 → 관리자/본인은 `GET /credentials/{credentialId}/verifications`로 조회 (인증 필요).

---

## 7. [D-2] 정정·폐기·재발급과 Tx 증빙

| 시나리오 | 호출 순서 |
|---|---|
| **폐기** | ① `POST /credentials/{id}/revoke` `{reason}` → ② `GET /credentials/{id}` polling (status=REVOKED, 블록체인 RevokeCertificate Tx 반영) → ③ `POST /credentials/verify`로 `REVOKED` 결과 시연 |
| **재발급** | ① `POST /credentials/{id}/reissue` `{reason, validUntil?}` → 202 + **새 credentialId** → ② 새 건 polling (ISSUED, versionNo+1) → ③ 구 건 조회 시 `SUPERSEDED` 확인 → ④ 구 credentialNo 검증 시 `SUPERSEDED`, 신규는 `VALID` |
| **Tx 증빙** | Credential 상세의 `vcHash`·`credentialNo`·`issuedAt` + `GET /vc/status/{credentialNo}`의 원장 검증 결과로 온체인 앵커링 확인 (Tx hash·block height는 관리자 모니터링 영역) |

---

## 8. 관리자 사전 준비 흐름 (시연 데이터 세팅 순서)

시연/리허설 전에 기관 관리자가 한 번 수행하는 순서:

```
① POST /institutions            (기관 등록, PLATFORM_ADMIN)
② POST /users + POST /users/{id}/roles   (관리자·강사 계정과 역할)
③ POST /courses                 (강좌 생성, status=DRAFT)
④ POST /courses/{id}/sessions   (회차 등록, 회차별 반복)
⑤ POST /courses/{id}/publish    (모집 시작 → RECRUITING)
   — 이후 1장(시민 E2E) 흐름 진행 —
```

---

## 9. 화면별 최초 로드 시 호출 요약 (프론트 참조)

| 화면 | 초기 호출 (병렬 가능) |
|---|---|
| 로그인 후 공통 | `GET /auth/session` |
| 강좌 목록 | `GET /courses` |
| 강좌 상세 | `GET /courses/{id}` + `GET /courses/{id}/sessions` + (관리자) `GET /courses/{id}/enrollments` |
| 회차 출결 탭 | `GET /sessions/{sessionId}/attendance` |
| 수강생 이수 탭 | `GET /enrollments/{id}/attendance-summary` + `GET /enrollments/{id}/completion` |
| 학습지갑 | `GET /users/me/credentials` |
| 수료증 상세 | `GET /credentials/{id}` + `GET /credentials/{id}/verifications` |
| 공개 검증 페이지 | (입력 후) `POST /credentials/verify` |

---

## 부록. 중간점검 체크리스트 ↔ 본 문서 매핑

| 체크리스트 | 본 문서 |
|---|---|
| B-1 시민 E2E | 1장 |
| B-2 상태전이 | 2장 |
| B-3 동적 QR | 3장 |
| B-4 부정출결 / B-5 출결보정 | 4장 |
| C-1 DID 연계 / C-2 VC 구조 | 5장 |
| C-4 VC 검증 / C-5 최소 공개 | 6장 |
| D-2 Tx 증빙 / 정정·폐기 | 7장 |
| F-3 최종 E2E | 1장 (전 구간) + 6·7장 |

> 다대구 DID 로그인은 `POST /auth/dadaegu/login`(Name:PhoneNum:Birthdate — DID+이름·생일·전화 저장, 재로그인 갱신)으로 구현됨 — 통합 명세 부록 참고. 미구현/협의 중 영역: Mitum 대구체인 API 연동 범위(D-1). 해당 구간은 확정 시 본 문서의 1-1 ①과 7장을 갱신할 것.
