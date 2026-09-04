# 다배움 API 호출 순서 정의서

> **기준 문서**: `docs/api/dabaeum-api-v1.yaml` (OpenAPI 3.1, **73 operations**) — 이 문서의 모든 단계·필드명·응답코드는 해당 명세와 1:1로 일치한다.
> **작성일**: 2026-08-12
> **읽는 법**: 각 operation의 요청/응답 스키마는 명세에 있으므로, 이 문서는 **"어떤 operationId를 어떤 순서로 호출하고, 응답의 어떤 필드를 다음 호출의 어디에 넣는지"** 만 정의한다.
>
> - **입력 ←** 열: 이전 단계 응답에서 가져와야 하는 값 (응답은 envelope `{data}` / `{data, page}` 안에 있음 — 이하 `data.` 생략)
> - 인증: 명세 전역 `bearerAuth` — `security: []` 명시된 공개 operation 외 전부 `Authorization: Bearer {accessToken}` 필요
> - 목록 operation의 `page/size/sort` 파라미터 표기는 생략

---

## 흐름 목록과 전체 연결

| # | 흐름 | 주체 |
|---|---|---|
| F0 | 로그인·세션 | 공통 |
| F1 | 기관·계정·역할 준비 | 플랫폼/기관 관리자 |
| F2 | 강사 신청·승인·배정 | 학습자→관리자 |
| F3 | 강좌 개설·공개 | 기관 관리자 |
| F4 | 강좌 탐색·수강신청 | 학습자 |
| F5 | 수강 심사·취소·철회 | 관리자/학습자 |
| F6 | 출결 (QR·직접입력·보정) | 관리자·강사+학습자 |
| F7 | 이수 평가·확정 | 관리자 |
| F8 | 수료증(VC) 발급 | 관리자 |
| F9 | 수료증 조회·다운로드 | 학습자 |
| F10 | 검증 (공개·무인증) | 누구나 |
| F11 | 폐기·재발급 | 관리자 |
| F12 | 학습 배지 | 관리자/학습자 |

```
[준비] F0 → F1 → (F2) → F3
[운영] F4 → F5 → F6 → F7 → F8 → F9 → F10 → (F11)

ID 체인: institutionId → courseId → sessionId ──→ attendanceId
                            │
                            └→ enrollmentId → completion.id → credentialId → credentialNo
```

앞 흐름의 마지막 산출 ID가 다음 흐름의 시작 입력이다.

---

## F0. 로그인·세션

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `loginLocalAccount` (공개) | LoginRequest: email, password | AuthToken: **accessToken**(이후 Bearer), tokenType, expiresIn, **session**(userId·roles 포함) |
| 2 | `getAuthSession` | — | Session: **userId**, **roles[]** — 화면 분기·토큰 재검증용. 1의 응답에 session이 포함되므로 앱 재시작 시 토큰 유효성 확인 용도로 사용 |

- 계정이 없으면 1 이전에 `signupLocalAccount` (공개).
- 내 프로필: `getCurrentUser` / `updateCurrentUser`.
- 어느 단계든 401 수신 시 F0-1로 복귀. 로그인 실패는 원인 구분 없이 전부 401 (명세 명시).

---

## F1. 기관·계정·역할 준비 (1회성)

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `createInstitution` | InstitutionCreateRequest: institutionCode, name (필수) | **institutionId** = `id` |
| 2 | `createUser` | UserCreateRequest: name (필수) | **userId** = `id` |
| 3 | `assignUserRole` | path: userId ← 2 / RoleAssignmentRequest: **role**(LEARNER·INSTITUTION_ADMIN·INSTRUCTOR·PLATFORM_ADMIN), institutionId ← 1 | 역할 부여 |

- 조회·수정 보조: `listInstitutions` `getInstitution` `updateInstitution` / `listUsers` `getUser` `updateUser` `changeUserStatus` / `listUserRoles` `revokeUserRole`
- 외부 identity(다대구 연계 대비): `listUserIdentities` `linkUserIdentity` `unlinkUserIdentity`
- **주의**: 수강할 학습자·강좌를 만들 관리자·강사 후보 모두 3단계 역할 부여가 선행되어야 이후 흐름에서 403/422가 나지 않는다.

---

## F2. 강사 신청·승인·배정 (강좌에 강사를 두는 경우)

### F2-A. 강사 신청 (학습자 본인)

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `applyInstructor` | path: institutionId / body(선택): applicationMessage | **applicationId** = `id` (status=PENDING). 신청자 ID는 인증 정보에서 — body로 지정 불가 |
| 2 | `listMyInstructorApplications` | — | 내 신청 상태 추적 (PENDING→APPROVED/REJECTED) |

### F2-B. 신청 심사 (기관 관리자)

| 순서 | operationId | 입력 ← | 비고 |
|---|---|---|---|
| 1 | `listInstitutionInstructorApplications` | path: institutionId, query: status=PENDING | → **applicationId** |
| 2a | `approveInstructorApplication` | path: applicationId ← 1 | 승인 시 해당 기관 INSTRUCTOR 역할 부여됨 |
| 2b | `rejectInstructorApplication` | path: applicationId / body: **rejectionReason** (필수) | |

- 상세: `getInstructorApplication`. 같은 기관에 처리 중 신청·기존 강사 역할이 있으면 409.

### F2-C. 강좌에 강사 배정 (강좌 생성 후 — F3 이후 가능)

| 순서 | operationId | 입력 ← | 비고 |
|---|---|---|---|
| 1 | `assignCourseInstructor` | path: courseId / CourseInstructorAssignRequest: **userId**(같은 기관 INSTRUCTOR 역할 보유자), **role**: MAIN\|ASSISTANT | MAIN 강사는 F6에서 QR 발급 가능 |
| 2 | `listCourseInstructors` | path: courseId | 배정 현황 |

- 변경·해제: `updateCourseInstructor`(role만), `removeCourseInstructor`.

---

## F3. 강좌 개설·공개 (기관 관리자)

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `createCourse` | CourseCreateRequest 필수: **institutionId** ← F1-1, courseCode, title, educationType, startDate, endDate, capacity | **courseId** = `id` (status=DRAFT) |
| 2 | `createCourseSession` | path: courseId ← 1 / CourseSessionCreateRequest 필수: sessionNo, startsAt, endsAt | **sessionId** = `id` (회차 수만큼 반복, status=SCHEDULED) |
| 3 | `publishCourse` | path: courseId ← 1 | status=**RECRUITING** → F4 시작 가능 |

- 수정: `updateCourse`, `updateCourseSession`, 조회: `getCourseSession`, 모집 마감: `closeCourse`.
- 1에서 courseCode 중복 시 409.

---

## F4. 강좌 탐색·수강신청 (학습자)

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `listCourses` | — | 선택한 강좌의 **courseId** |
| 2 | `getCourse` | path: courseId ← 1 | status=RECRUITING 확인, 정원·기간·학점 표시 |
| 3 | `listCourseSessions` | path: courseId ← 1 | 회차 일정 표시 |
| 4 | `createEnrollment` | path: courseId ← 1 / EnrollmentCreateRequest: **userId** ← F0의 session.userId (필수), applicationType 생략 시 SELF | 201 → **enrollmentId** = `id` (status=APPLIED) |
| 5 | `getEnrollment` | path: enrollmentId ← 4 | 신청 상태 추적 (APPLIED→APPROVED 대기) |

- 4의 실패: 409(중복 신청), 422(정원 초과·모집기간 아님).

---

## F5. 수강 심사 (관리자) · 취소/철회 (학습자)

| 순서 | operationId | 입력 ← | 비고 |
|---|---|---|---|
| 1 | `listCourseEnrollments` | path: courseId | status=APPLIED 건 → **enrollmentId** |
| 2a | `approveEnrollment` | path: enrollmentId ← 1 | APPLIED→**APPROVED** (F6의 전제조건) |
| 2b | `rejectEnrollment` | path: enrollmentId ← 1 / body: reason (필수) | APPLIED→REJECTED |

병행 경로:

- 대리신청(관리자): `createProxyEnrollment` — path: courseId / body: **userId** → 이후 2a 동일
- 취소(학습자): `cancelEnrollment` — APPLIED 상태에서만
- 철회(학습자): `withdrawEnrollment` — APPROVED 상태에서만

---

## F6. 출결

### F6-A. QR 출결 (기본 경로)

| 순서 | 주체 | operationId | 입력 ← | 비고 |
|---|---|---|---|---|
| 1 | 관리자 | `updateCourseSession` | path: sessionId ← F3-2 / body: `{status:"OPEN", attendanceOpensAt, attendanceClosesAt}` | QR 발급의 전제조건 (회차 상태 OPEN + 시간창) |
| 2 | 관리자·MAIN강사 | `issueAttendanceQrToken` | path: sessionId | 201 → AttendanceQrToken: **token**(QR 렌더링), **expiresAt**. 호출 권한: PLATFORM_ADMIN·같은 기관 INSTITUTION_ADMIN·같은 과정 MAIN 강사 (명세 명시) |
| 3 | 〃 | (2 반복) | — | **expiresAt 도래 전에 2를 재호출**해 QR 갱신 (재발급 시 이전 토큰 대체 — 명세 명시). 화면 닫을 때까지 루프 |
| 4 | 학습자 | `recordAttendance` | path: sessionId / AttendanceCreateRequest: **enrollmentId** ← F5, attendanceMethod=`"QR"`, status=`"PRESENT"`, source=`"APP"`, **qrToken** ← 스캔 값 | 201 → **attendanceId** = `id`. qrToken은 QR 방식일 때 필수 (명세 명시) |
| 5 | 관리자 | `listSessionAttendance` | path: sessionId | 출결 현황 갱신 |
| 6 | 관리자 | `updateCourseSession` | path: sessionId / body: `{status:"COMPLETED"}` | 출결 마감 — F7 전에 전 회차 수행 |

- 4의 실패 분기: **422**(토큰 만료·위조 → 새 QR 재스캔 안내), **409**(동일 회차 중복 기록), **403**(미승인 수강생·본인 아님).
- `checkedAt`은 보내지 않는다 — QR 기록 시각은 서버 기준.

### F6-B. 관리자 직접 입력

`recordAttendance` — body: `{enrollmentId, attendanceMethod:"ADMIN", status, source:"ADMIN_WEB"}`. **qrToken 금지** (명세: ADMIN·EXTERNAL에서는 forbidden).

### F6-C. 보정

| 순서 | operationId | 입력 ← |
|---|---|---|
| 1 | `listSessionAttendance` | path: sessionId → 대상 **attendanceId** |
| 2 | `adjustAttendance` | path: attendanceId ← 1 / AttendanceAdjustmentRequest: **status**, **reason** (둘 다 필수) |
| 3 | `getAttendance` | path: attendanceId — 보정 결과 확인 |
| 4 | `getAttendanceSummary` | path: enrollmentId — 집계 반영 확인 |

---

## F7. 이수 평가·확정 (관리자)

**전제**: 대상 수강생의 전 회차가 F6-A-6(COMPLETED) 처리된 상태.

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `getAttendanceSummary` | path: enrollmentId | AttendanceSummary: **attendanceRate** (서버 집계) |
| 2 | `evaluateCompletion` | path: enrollmentId / CompletionEvaluationRequest: **attendanceRate ← 1의 값 그대로**, **completedMinutes** (필수), creditValue? | Completion: status=**ELIGIBLE** 또는 NOT_COMPLETED |
| 3 | `confirmCompletion` | path: enrollmentId | status=**COMPLETED** → **completion.id** (F8의 completionId) |
| 4 | `getCompletion` | path: enrollmentId | 상태 재확인. 평가 전이면 404 (= 정상 흐름, 오류 아님) |

- 2에서 서버 집계와 다른 값 제출 시 409 — 1을 생략하지 말 것. 3은 ELIGIBLE에서만 성공.

---

## F8. 수료증(VC) 발급 (관리자) — 비동기 202 + polling

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `issueCredential` | path: completionId ← F7-3 / **Idempotency-Key 헤더 필수** / body(선택): validUntil | **202** + Credential(status=PENDING) + **`Location` 헤더 = 상태 조회 URI** (명세 명시) |
| 2 | `getCredential` | path: credentialId ← 1의 `id` (또는 Location 헤더 그대로 GET) | **status** 확인 |
| 3 | — | (polling) | status ∈ {PENDING, ISSUING} 동안 2~3초 간격으로 2 반복 (명세: "202 응답 후 상세 조회 API로 PENDING·ISSUING·ISSUED·FAILED 상태를 polling") |
| 4 | — | 종료 | **ISSUED** → credentialNo·vcHash·issuedAt 확정 / **FAILED** → 새 Idempotency-Key로 1 재시도 |

- 1의 전제: Completion status=COMPLETED (아니면 409). 이미 발급된 이수 → 409.
- 발급은 블록체인 앵커링 포함 — 수 초~수십 초 소요될 수 있음.

---

## F9. 수료증 조회·다운로드 (학습자)

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `listCurrentUserCredentials` | 인증 컨텍스트의 현재 사용자 (userId 전달 없음) | 목록 → **credentialId**, **credentialNo** |
| 2 | `getCredential` | path: credentialId ← 1 | 상태·유효기간·vcHash |
| 3 | `downloadCredentialDocument` | path: credentialId ← 1 | **VC JWT 원문** — Content-Type `application/vc+jwt`, **raw 텍스트 (envelope 아님)**. status=ISSUED에서만 |
| 4 | `listCredentialVerifications` | path: credentialId ← 1 | 검증 이력 (본인·기관 관리자·플랫폼 관리자) |

---

## F10. 검증 (공개 — `security: []`, 인증 불필요)

### F10-A. 번호/해시로 상태 검증

| 순서 | operationId | 입력 | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `verifyCredential` | CredentialVerifyRequest: **credentialNo 또는 credentialHash 중 정확히 하나**(anyOf — 없는 필드는 **키 생략**, 빈 문자열 금지), verificationType(QR\|API\|ADMIN), requesterType(INDIVIDUAL\|INSTITUTION\|EXTERNAL_ORGANIZATION\|SYSTEM) | CredentialVerification: **result** = VALID·INVALID·REVOKED·SUPERSEDED·EXPIRED·NOT_FOUND·ERROR |
| 2 | `getPublicCredentialStatus` | path: credentialNo | (선택) PublicCredentialStatus: credentialNo, **status**, checkedAt — DB·서명·해시·**Fabric 원장까지 검증** (명세 명시). `Cache-Control: no-store`. 원장 서비스 없으면 **503** → "원장 확인 불가" 폴백 |

- 명세 명시: raw VC/proof는 받지 않음. 공개 호출의 검증 이력은 requesterType=INDIVIDUAL로 기록.

### F10-B. VC 원문(JWT)을 받은 검증자의 자체 검증

| 순서 | operationId | 용도 |
|---|---|---|
| 1 | — (JWT 파싱) | 발급기관(institutionId)·credentialNo 식별 |
| 2 | `getCredentialIssuer` | path: institutionId → 발급기관 **공개키** 획득, 서명 로컬 검증 |
| 3 | `getLifelongEducationContextV1` / `getLifelongEducationVocabularyV1` | JSON-LD `@context`·용어 해석 (필요 시) |
| 4 | `getPublicCredentialStatus` | 서명이 유효해도 **현행 상태**(폐기·대체·만료) 확인 필수 |

---

## F11. 폐기·재발급 (관리자) — 둘 다 202 + polling (F8 패턴)

```
폐기:   revokeCredential  (path: credentialId, body.reason 필수, Idempotency-Key)
        → 202 → getCredential polling → status=REVOKED
        → (확인) verifyCredential → result=REVOKED

재발급: reissueCredential (path: credentialId, body.reason 필수·validUntil?, Idempotency-Key)
        → 202 응답 data.id = **새 credentialId** (versionNo+1, 같은 credentialGroupId)
        → 새 credentialId로 getCredential polling → ISSUED
        → 이전 credentialId는 status=SUPERSEDED로 전이
```

- 대상이 ISSUED 상태가 아니면 409.

---

## F12. 학습 배지

| 순서 | operationId | 입력 ← | 응답에서 쓸 값 |
|---|---|---|---|
| 1 | `issueLearningBadge` | path: credentialId (ISSUED 건) | LearningBadge: **badgeId** = `id`, status(PENDING→ISSUED), nftTokenId |
| 2 | `listUserBadges` | path: userId | 내 배지 목록 |
| 3 | `getLearningBadge` | path: badgeId ← 1 | 상세 |

---

## 부록 A. operationId → 흐름 역인덱스 (73개 전체)

| operationId | 위치 | operationId | 위치 |
|---|---|---|---|
| getSystemPing | 헬스체크 | listCourseEnrollments | F5-1 |
| signupLocalAccount | F0 선행 | createEnrollment | F4-4 |
| loginLocalAccount | F0-1 | createProxyEnrollment | F5 대리 |
| getAuthSession | F0-2 | getEnrollment | F4-5 |
| getCurrentUser / updateCurrentUser | F0 보조 | approveEnrollment / rejectEnrollment | F5-2 |
| createInstitution | F1-1 | cancelEnrollment / withdrawEnrollment | F5 학습자 |
| listInstitutions / getInstitution / updateInstitution | F1 보조 | issueAttendanceQrToken | F6-A-2 |
| createUser | F1-2 | recordAttendance | F6-A-4 · F6-B |
| listUsers / getUser / updateUser / changeUserStatus | F1 보조 | listSessionAttendance | F6-A-5 · F6-C-1 |
| assignUserRole | F1-3 | getAttendance / adjustAttendance | F6-C |
| listUserRoles / revokeUserRole | F1 보조 | getAttendanceSummary | F6-C-4 · F7-1 |
| listUserIdentities / linkUserIdentity / unlinkUserIdentity | F1 (SSO 대비) | getCompletion | F7-4 |
| applyInstructor | F2-A-1 | evaluateCompletion / confirmCompletion | F7-2 · 3 |
| listMyInstructorApplications | F2-A-2 | issueCredential | F8-1 |
| listInstitutionInstructorApplications | F2-B-1 | getCredential | F8-2 · F9-2 · F11 |
| getInstructorApplication | F2-B 보조 | listCurrentUserCredentials | F9-1 |
| approveInstructorApplication / rejectInstructorApplication | F2-B-2 | downloadCredentialDocument | F9-3 |
| assignCourseInstructor / listCourseInstructors | F2-C | listCredentialVerifications | F9-4 |
| updateCourseInstructor / removeCourseInstructor | F2-C 보조 | verifyCredential | F10-A-1 |
| createCourse | F3-1 | getPublicCredentialStatus | F10-A-2 · F10-B-4 |
| listCourses / getCourse / updateCourse | F4-1·2 / F3 보조 | getCredentialIssuer | F10-B-2 |
| publishCourse / closeCourse | F3-3 | getLifelongEducationContextV1 / ...VocabularyV1 | F10-B-3 |
| createCourseSession / listCourseSessions | F3-2 / F4-3 | revokeCredential / reissueCredential | F11 |
| getCourseSession / updateCourseSession | F3 보조 / F6-A-1·6 | issueLearningBadge / listUserBadges / getLearningBadge | F12 |

## 부록 B. 리소스 상태 → 호출 가능한 다음 operation

| 상태 | 다음 operation |
|---|---|
| Course.DRAFT | publishCourse |
| Course.RECRUITING | createEnrollment, closeCourse |
| CourseSession.SCHEDULED | updateCourseSession(→OPEN) |
| CourseSession.OPEN | issueAttendanceQrToken, recordAttendance, updateCourseSession(→COMPLETED) |
| InstructorApplication.PENDING | approveInstructorApplication, rejectInstructorApplication |
| Enrollment.APPLIED | approveEnrollment, rejectEnrollment, cancelEnrollment |
| Enrollment.APPROVED | recordAttendance(대상), withdrawEnrollment, evaluateCompletion |
| Completion.ELIGIBLE | confirmCompletion |
| Completion.COMPLETED | issueCredential |
| Credential.PENDING / ISSUING | getCredential (polling만) |
| Credential.ISSUED | downloadCredentialDocument, revokeCredential, reissueCredential, issueLearningBadge |

이 표 밖의 조합은 서버가 409/422로 거부한다 (각 operation의 응답 코드 정의 참조). 서버 내부 검증 규칙·에러 코드 상세는 `dabaeum-api-integration-spec.md` 참조.
