# 다배움 API 연동 명세 — 호출 순서·데이터 흐름·서버 검증 규칙

> **기준**: 백엔드 구현 (`src/main/java/com/adn/dabaeum`) + `docs/api/dabaeum-api-v1.yaml` (OpenAPI 3.1, 63 operations)
> **작성일**: 2026-08-12
> **성격**: 어떤 클라이언트(관리자 웹·학습자 앱·외부 연동)를 만들든 공통으로 지켜야 하는 **서버 계약**을 정의한다. 호출 순서, 각 API의 사전조건(서버가 실제로 검증하는 규칙), 실패 시 반환되는 에러 코드를 백엔드 코드 기준으로 기술한다.

---

## 1. 공통 계약

| 항목 | 내용 |
|---|---|
| Base URL | `/api/v1` |
| 인증 | `Authorization: Bearer {JWT}`. 공개 API는 명세에 `security: []` 표기 (`/credentials/verify`, `/vc/*`, `/system/ping`, `/auth/*`) |
| 응답 envelope | 단건 `{"data": {...}}`, 목록 `{"data": [...], "page": {page,size,totalElements,...}}` |
| 페이지 | `page`(0-base), `size`(1~100 — 초과 시 400), `sort`(`필드,asc|desc`) |
| 에러 응답 | `{"code": "<ApiErrorCode>", "message": "...", "details": [...], "requestId": "..."}` — `requestId`는 서버 로그 추적 키 |
| 시간 | 모든 시각 판정은 **서버 시계** 기준 (QR 만료, 출결 시각 등). 클라이언트 시계로 판정하지 말 것 |

### 1.1 Idempotency-Key 계약

상태 변경 API는 `Idempotency-Key` 헤더(UUID 권장)를 받는다. **Credential 발급·폐기·재발급은 서버가 키를 저장·대조한다**:

- 같은 키 + 같은 요청 재전송 → 최초 결과 그대로 반환 (안전한 재시도)
- 같은 키 + **다른** 요청 → `409 CREDENTIAL_IDEMPOTENCY_CONFLICT`
- 같은 키 동시 요청 → 한쪽만 성공, 다른 쪽 `409 CREDENTIAL_IDEMPOTENCY_CONFLICT`

QR 토큰 발급·출결 기록·조회성 API는 Idempotency-Key를 사용하지 않는다 (출결 중복은 서버가 세션+수강 unique 제약으로 자연 차단).

### 1.2 핵심 ID 체인 (데이터 흐름의 뼈대)

```
institutionId → courseId → sessionId ──────────────┐
                    │                              ├→ attendanceId
                    └→ enrollmentId ───────────────┤
                              │                    └→ attendance-summary (집계)
                              └→ completionId → credentialId → credentialNo / vcHash
```

앞 단계 응답 `data.id`가 다음 단계 path parameter가 된다.

---

## 2. 사전 준비 흐름 (기관·계정·강좌)

```
① POST /institutions                          (PLATFORM_ADMIN)
② POST /users → POST /users/{id}/roles        (기관 관리자·학습자 역할 부여)
③ POST /courses                               (status=DRAFT)
④ POST /courses/{id}/sessions                 (회차별 반복, status=SCHEDULED)
⑤ POST /courses/{id}/publish                  (DRAFT→RECRUITING, 이외 상태면 409 COURSE_STATUS_CONFLICT)
```

**서버 검증**: 기관 코드 중복 `409 INSTITUTION_CODE_CONFLICT`, 역할 중복 `409 ROLE_ASSIGNMENT_CONFLICT`, 회차 번호 중복 `409 COURSE_SESSION_CONFLICT`. 기관 범위를 벗어난 관리자 요청은 `403 INSTITUTION_SCOPE_FORBIDDEN`.

---

## 3. 수강신청 흐름

```
학습자   ① GET  /courses, GET /courses/{id}, GET /courses/{id}/sessions
        ② POST /courses/{courseId}/enrollments            → APPLIED
관리자   ③ GET  /courses/{courseId}/enrollments            (심사 목록)
        ④ POST /enrollments/{id}/approve | /reject{reason} → APPROVED | REJECTED
학습자   ⑤ POST /enrollments/{id}/cancel   (APPLIED 상태에서만)
        ⑥ POST /enrollments/{id}/withdraw  (APPROVED 상태에서만)
관리자   대리신청: POST /courses/{id}/proxy-enrollments {userId}
```

**상태 기계 (서버 강제)**

```
APPLIED ─approve→ APPROVED ─withdraw→ WITHDRAWN
   │ ├─reject→ REJECTED
   │ └─cancel→ CANCELLED          WAITLISTED: 정원 초과 시
```

**서버 검증**: 중복 신청 `409 ENROLLMENT_CONFLICT`, 잘못된 상태에서 전이 `409 ENROLLMENT_STATUS_CONFLICT`, 정원 초과 `422 COURSE_CAPACITY_EXCEEDED`, 대리신청 대상자에게 해당 기관 LEARNER 역할 없으면 `422 ROLE_REQUIRED`.

---

## 4. 출결 흐름 (QR·관리자 입력·보정)

### 4.1 회차 출결 열기 (관리자)

```
① PUT /sessions/{sessionId}  body: { status: "OPEN", attendanceOpensAt, attendanceClosesAt }
```

QR 발급·출결 기록은 이 상태 전이가 선행되어야 한다. 회차 상태: `SCHEDULED → OPEN → COMPLETED / CANCELLED`.

### 4.2 QR 토큰 발급 (관리자, 반복 호출)

```
② POST /sessions/{sessionId}/qr-token   → { token, expiresAt }
```

**서버 규칙 (`DefaultAttendanceQrTokenApplicationService`)**

- TTL **30초**. 단, `attendanceClosesAt`이 더 이르면 그 시각까지로 단축: `expiresAt = min(now+30s, attendanceClosesAt)`
- 회차 상태가 `OPEN`이 아니면 `409 ATTENDANCE_CONFLICT` ("Attendance session is not open")
- 출결 시간창 밖이면 `409 ATTENDANCE_WINDOW_CLOSED`
- 호출 권한: 해당 강좌의 세션 관리자(기관 관리자/강사) — 아니면 `403`
- 재발급 시 이전 토큰은 대체(무효화)됨. Idempotency-Key 미사용
- 토큰은 HMAC 서명된 opaque 문자열 (tokenId·sessionId·발급/만료시각·nonce 포함). **클라이언트는 파싱·해석 금지**, 그대로 전달만 한다

클라이언트는 `expiresAt` 도래 전(권장: 만료 5초 전) ②를 재호출해 QR을 갱신하는 루프를 돌린다.

### 4.3 출결 기록

```
③ POST /sessions/{sessionId}/attendance
   body: { enrollmentId, attendanceMethod: "QR"|"ADMIN", status, source, qrToken?, checkedAt? }
```

**서버 검증 순서 (`DefaultAttendanceApplicationService.record`)** — 실패 시점별 에러:

| 순서 | 검증 | 실패 시 |
|---|---|---|
| 1 | `attendanceMethod=EXTERNAL` 거부 | `422 ATTENDANCE_METHOD_NOT_SUPPORTED` |
| 2 | 회차·강좌·수강신청 존재 | `404 *_NOT_FOUND` |
| 3 | 수강 상태 `APPROVED` + 같은 강좌 소속 | `409/422` (승인 안 된 수강생 차단) |
| 4-QR | 호출자 == enrollment의 학습자 본인 | `403 FORBIDDEN` (타인 토큰 대리 스캔 차단) |
| 5-QR | 토큰 서명·만료·sessionId 일치 검증 | `422 ATTENDANCE_QR_INVALID` / `422 ATTENDANCE_QR_EXPIRED` |
| 4-ADMIN | 호출자가 강좌 관리자 | `403` |
| 6 | 동일 (sessionId, enrollmentId) 중복 | `409 ATTENDANCE_CONFLICT` (DB unique 제약으로도 이중 차단) |

- **QR 기록의 `checkedAt`은 서버가 현재 시각으로 강제 설정** — 클라이언트가 보낸 값 무시
- ADMIN 기록은 `checkedAt` 지정 가능 (미지정 시 서버 시각)
- QR 기록에는 검증된 `qrTokenId`가 함께 저장됨 (감사 추적)

### 4.4 출결 보정·집계

```
④ PATCH /attendance/{attendanceId}   body: { status, reason }   ← reason 필수 (없으면 400)
⑤ GET   /sessions/{sessionId}/attendance                        (회차별 목록)
⑥ GET   /enrollments/{enrollmentId}/attendance-summary          (출석률 집계)
```

보정은 원 기록을 덮어쓰지 않고 사유와 함께 이력으로 보존된다(append-only). 집계(⑥)는 서버 계산 — 클라이언트가 재계산하지 말 것.

---

## 5. 이수 흐름

```
① GET  /enrollments/{id}/attendance-summary        (서버 집계 확인)
② POST /enrollments/{id}/completion/evaluate       body: { attendanceRate, completedMinutes, creditValue?, failureReason? }
③ POST /enrollments/{id}/completion/confirm
④ GET  /enrollments/{id}/completion                (상태 확인, 평가 전이면 404 COMPLETION_NOT_FOUND)
```

**상태 기계**: `PENDING_EVALUATION ─evaluate→ ELIGIBLE | NOT_COMPLETED ─confirm(ELIGIBLE만)→ COMPLETED`

**서버 검증**: 제출한 attendanceRate·completedMinutes가 서버 집계와 다르면 `409 COMPLETION_METRICS_CONFLICT` — 반드시 ①의 값을 그대로 제출한다. ELIGIBLE 아닌 상태에서 confirm 하면 `409 COMPLETION_STATUS_CONFLICT`.

`confirm` 성공 시 서버는 **outbox 이벤트**를 기록한다 (6장 비동기 발급의 트리거 준비).

---

## 6. Credential(VC) 발급 흐름 — 비동기 계약

### 6.1 발급

```
① POST /completions/{completionId}/credentials     (Idempotency-Key 필수)
   → 202 Accepted, data = Credential(status=PENDING, credentialId 포함)
② GET  /credentials/{credentialId}                 (상태 polling, 권장 2~3초 간격)
③ status: PENDING → ISSUING → ISSUED (credentialNo·vcHash·issuedAt 확정)
                              └→ FAILED
```

**서버 검증 (`DefaultCredentialApplicationService`)**

| 조건 | 실패 시 |
|---|---|
| completion.status ≠ `COMPLETED` | `409 COMPLETION_NOT_CONFIRMED` |
| 해당 이수에 유효 credential 이미 존재 | `409 CREDENTIAL_ALREADY_EXISTS` |
| Idempotency-Key 재사용 (다른 요청) | `409 CREDENTIAL_IDEMPOTENCY_CONFLICT` |

**비동기 처리 (서버 내부 — 클라이언트는 polling만)**

- 발급은 outbox 패턴으로 처리: 워커가 PENDING 이벤트를 집어 VC 생성(서명)→해시→Fabric 앵커링을 수행
- 실패 시 지수 백오프로 자동 재시도 (최대 5회, 초기 30초). 재시도 중에는 `PENDING/ISSUING` 유지
- 최종 실패 시 `FAILED` — 클라이언트는 새 Idempotency-Key로 ①을 다시 호출해 재발급 요청
- 블록체인 단계 오류는 서버 내부 코드로 분류됨: `FABRIC_SUBMIT_FAILED`, `FABRIC_COMMIT_TIMEOUT`, `FABRIC_COMMIT_INVALID`, `FABRIC_LEDGER_CONFLICT`
- 60초 이상 상태 변화가 없으면 정합 워커(reconciliation)가 원장과 대조해 상태를 복구한다 — 클라이언트는 타임아웃으로 단정하지 말고 "처리 지연" 표시 권장

### 6.2 폐기·재발급

```
폐기:   POST /credentials/{id}/revoke  { reason }   → polling → REVOKED
재발급: POST /credentials/{id}/reissue { reason, validUntil? }
        → 202, data = 새 Credential(PENDING, versionNo+1, 같은 credentialGroupId)
        → 새 credentialId polling → ISSUED. 이전 건은 SUPERSEDED로 전이
```

**서버 검증**: `ISSUED` 상태가 아닌 건 폐기/재발급 시 `409 CREDENTIAL_STATE_CONFLICT`. reason 필수(400). Idempotency-Key 계약은 발급과 동일.

**Credential 상태 기계 (전체)**

```
PENDING → ISSUING → ISSUED ─revoke→ REVOKED
              └→ FAILED     ├─reissue→ (새 버전 PENDING→…→ISSUED, 본 건 SUPERSEDED)
                            └─validUntil 경과→ EXPIRED
```

### 6.3 원문 조회

```
GET /credentials/{credentialId}/document   → application/vc+jwt (Compact JWS 원문, JSON envelope 아님)
```

`ISSUED` 상태에서만 의미 있음. 접근 권한: 본인·해당 기관 관리자·플랫폼 관리자.

---

## 7. 검증 흐름 (공개 API — 무인증)

### 7.1 상태 검증

```
POST /credentials/verify
body: { credentialNo XOR credentialHash, verificationType: "QR"|"API"|"ADMIN",
        requesterType: "INDIVIDUAL"|"INSTITUTION"|"EXTERNAL_ORGANIZATION"|"SYSTEM", requesterId? }
→ 200, result: VALID | INVALID | REVOKED | SUPERSEDED | EXPIRED | NOT_FOUND | ERROR
```

**계약 주의사항**

- `credentialNo`와 `credentialHash`는 **정확히 하나만** 제공 (`anyOf` + `minLength:1` + `additionalProperties:false`). **값이 없는 필드는 키 자체를 생략** — 빈 문자열 전송 시 400
- raw VC/proof는 받지 않는다 — 번호 또는 SHA-256 해시(64 hex)만 전송
- 모든 검증 시도는 서버가 이력으로 기록 → `GET /credentials/{id}/verifications` (인증 필요)로 조회
- 응답에 개인정보(성명·성적) 없음 — 최소공개 원칙

### 7.2 원장 포함 공개 상태 조회

```
GET /vc/status/{credentialNo}     → DB·서명·해시·Fabric 원장까지 검증한 최소 상태
```

- `Cache-Control: no-store` — 캐시 금지
- 원장 검증 서비스 미가동 시 `503` — 클라이언트는 "원장 확인 불가" 폴백 처리

### 7.3 VC 자체 검증용 공개 리소스 (제3자 검증자)

| Method / Path | 용도 |
|---|---|
| `GET /vc/issuers/{institutionId}` | 발급기관 공개키(Ed25519)·Issuer 식별자 — JWT 서명 로컬 검증용 |
| `GET /vc/contexts/lifelong-education/v1` | VC `@context` JSON-LD |
| `GET /vc/vocabulary/lifelong-education/v1` | 용어 정의 |

**제3자 검증 데이터 흐름**: VC JWT 입수(파일/QR) → JWT 헤더에서 발급기관 식별 → issuers API로 공개키 획득 → 서명 로컬 검증 → `/vc/status/{credentialNo}`로 현행 상태(폐기·대체 반영) 확인.

---

## 8. E2E 통합 시퀀스 (전 구간 요약)

```
[준비]    기관·계정·역할 → 강좌 생성 → 회차 등록 → publish
[신청]    학습자 신청(APPLIED) → 관리자 승인(APPROVED)
[출결]    회차 OPEN → QR 발급(30초 갱신 루프) → 학습자 스캔 기록 → (필요시 보정) → 회차 COMPLETED
[이수]    attendance-summary 확인 → evaluate(서버 집계값 그대로) → confirm(COMPLETED)
[발급]    발급 요청(202) → polling → ISSUED (credentialNo·vcHash 확정, Fabric 앵커링 완료)
[활용]    document(JWT) 다운로드 / 학습지갑 목록
[검증]    /credentials/verify (무인증) → VALID · /vc/status/{no} (원장 확인)
[수명주기] revoke → REVOKED / reissue → 새 버전 ISSUED + 구 버전 SUPERSEDED
```

각 단계의 산출 ID가 다음 단계 입력이 된다 (1.2 ID 체인).

---

## 9. 에러 코드 레퍼런스 (`ApiErrorCode` 전체)

| 코드 | 상황 |
|---|---|
| `BAD_REQUEST` / `VALIDATION_FAILED` | 요청 형식·스키마 위반 (`details[]`에 필드 목록) |
| `UNAUTHORIZED` / `AUTHENTICATION_FAILED` | 토큰 없음·만료·로그인 실패 |
| `FORBIDDEN` / `INSTITUTION_SCOPE_FORBIDDEN` / `USER_STATUS_FORBIDDEN` | 권한·기관 범위·계정 상태 |
| `INSTITUTION_NOT_FOUND` / `INSTITUTION_CODE_CONFLICT` | 기관 |
| `USER_NOT_FOUND` / `USER_STATUS_CONFLICT` | 사용자 |
| `IDENTITY_NOT_FOUND` / `IDENTITY_CONFLICT` / `IDENTITY_REQUIRED` / `IDENTITY_NOT_VERIFIED` | 외부 identity(다대구 연계 대비) |
| `ROLE_NOT_FOUND` / `ROLE_ASSIGNMENT_CONFLICT` / `ROLE_REQUIRED` | 역할 |
| `COURSE_NOT_FOUND` / `COURSE_CONFLICT` / `COURSE_STATUS_CONFLICT` | 강좌·상태 전이 |
| `COURSE_SESSION_NOT_FOUND` / `COURSE_SESSION_CONFLICT` | 회차 |
| `ENROLLMENT_NOT_FOUND` / `ENROLLMENT_CONFLICT` / `ENROLLMENT_STATUS_CONFLICT` / `COURSE_CAPACITY_EXCEEDED` | 수강신청 |
| `ATTENDANCE_NOT_FOUND` / `ATTENDANCE_CONFLICT` / `ATTENDANCE_WINDOW_CLOSED` / `ATTENDANCE_QR_INVALID` / `ATTENDANCE_QR_EXPIRED` / `ATTENDANCE_METHOD_NOT_SUPPORTED` | 출결 (4장) |
| `COMPLETION_NOT_FOUND` / `COMPLETION_STATUS_CONFLICT` / `COMPLETION_METRICS_CONFLICT` / `COMPLETION_NOT_CONFIRMED` | 이수 (5장) |
| `CREDENTIAL_NOT_FOUND` / `CREDENTIAL_ALREADY_EXISTS` / `CREDENTIAL_STATE_CONFLICT` / `CREDENTIAL_IDEMPOTENCY_CONFLICT` | 발급 (6장) |
| `CREDENTIAL_PROOF_GENERATION_FAILED` / `CREDENTIAL_PROOF_INVALID` / `CREDENTIAL_HASH_MISMATCH` / `CREDENTIAL_VERIFICATION_FAILED` | 서명·해시·검증 |
| `FABRIC_SUBMIT_FAILED` / `FABRIC_COMMIT_TIMEOUT` / `FABRIC_COMMIT_INVALID` / `FABRIC_LEDGER_CONFLICT` | 블록체인 앵커링 (주로 비동기 워커 내부 — FAILED 상태의 원인) |
| `INSTRUCTOR_APPLICATION_*` / `COURSE_INSTRUCTOR_*` | 강사 지원·배정 |
| `INTERNAL_SERVER_ERROR` | 서버 오류 (`requestId`로 추적) |

**HTTP 매핑 일반 규칙**: 404=NOT_FOUND 계열, 409=CONFLICT 계열(상태 전이·중복·멱등성), 422=비즈니스 규칙(정원·QR 만료·미지원 방식), 400=형식 오류, 503=원장 서비스 불가.

---

## 부록. 미확정 영역

- **다대구 DID 로그인**: `GET /auth/dadaegu/config`(siteId) → 프론트 `DIDLogin.loginPersonal({requiredVC:'Name:PhoneNum:Birthdate'})` → 콜백의 암호화된 `did`·`name`·`birthdate`·`phoneNumber`를 `POST /auth/dadaegu/login`으로 전달 → 서버가 RSA 복호화 후 `tb_user_identities(DADAEGU)`에 DID, `tb_users`에 이름·생년월일·전화를 저장하고 JWT(`provider: DADAEGU`) 발급. 재로그인 시 프로필을 다대구 값으로 갱신. ci·성별·내외국인은 복호화·저장하지 않으며, 이메일은 로그인 후 `PUT /users/me`(선택). 이후 호출 순서는 LOCAL 로그인과 동일. local/dev 프로필에서 동작. **통합 회원**: 로그인된 사용자가 `POST /users/me/identities/dadaegu`(QR 암호문)로 DID 를, `POST /users/me/identities/local`(email·password)로 LOCAL 수단을 추가한다. 두 수단은 동등하며 같은 userId 로 인증된다. 프로필(이름·전화·생년월일)은 QR 로그인·QR 연결 때만 다대구 값으로 갱신되고, 계정 병합은 지원하지 않는다(409)
- **Mitum 대구체인 API**: 협의 단계. 확정 시 6장 앵커링 대상과 7.2 원장 검증 대상이 갱신될 수 있음
- **QR 전달 포맷**: 서버는 opaque 토큰만 발급·검증한다. QR에 토큰만 담을지, `sessionId`를 포함한 딥링크로 담을지는 클라이언트 간 합의 사항 (서버 계약 아님 — 단, 출결 기록에는 `sessionId`가 path로 필요하므로 스캔 측이 sessionId를 알 수 있는 형식이어야 함)
