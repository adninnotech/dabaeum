# 다배움 프론트엔드 API 호출 흐름

회원가입부터 Credential 폐기까지 **31단계 기본 흐름 + 7개 곁가지**를
순서 · 호출 주체 · 변수 전달로 정리한 문서입니다. 뒤쪽에 전체 **78개 operation 인덱스**가 있습니다.

경로·필수 필드는 `docs/api/dabaeum-api-v1.yaml`, 권한·상태 규칙은 서버 소스를 단일 기준으로 합니다.
`{baseUrl}`은 각자 환경 주소로 바꿔 쓰세요.

## 먼저 알아야 할 것

**토큰은 두 종류뿐입니다.** `{accessToken}`(학습자 계정 — 강사 승인을 받아도 같은 토큰)과
`{adminAccessToken}`(별도 기관관리자 계정).

**역할은 JWT에 들어 있지 않고 요청마다 DB에서 읽습니다.** 권한이 바뀌어도 토큰을 다시 받을 필요가 없습니다.
`401`은 토큰 만료, `403`은 역할·기관 범위 문제입니다.

## 순서만으로 안 되는 것들

| 규칙 | 내용 |
|---|---|
| 토큰에 역할 없음 | 강사 승인 즉시 기존 토큰에 INSTRUCTOR 적용. 재로그인 불필요 |
| 회차는 SCHEDULED로 생성 | `PUT /sessions/{sessionId}` 로 OPEN 전환해야 QR 발급 가능. 아니면 409 ATTENDANCE_CONFLICT |
| 출결창 | 출결창 밖이면 409 ATTENDANCE_WINDOW_CLOSED. QR 토큰 TTL 60초(기본값, `dabaeum.attendance.qr-token.ttl`로 조정) |
| 이수 평가 | 서버가 출결 요약을 자체 계산해 대조. 임의 값이면 409 COMPLETION_METRICS_CONFLICT |
| 재발급 순서 | 재발급은 ISSUED에만 가능. 폐기 후 재발급은 409 CREDENTIAL_STATE_CONFLICT |
| 정원 초과 | 오류 아님. WAITLISTED로 접수되고 201 반환 |
| 과정 상태 | RECRUITING에서만 수강신청 접수. 아니면 409 COURSE_STATUS_CONFLICT |
| 취소 vs 철회 | cancel은 승인 전(APPLIED·WAITLISTED), withdraw는 승인 후(APPROVED) |
| 출결 권한 반전 | QR은 본인만, 수동은 관리자·MAIN 강사만. EXTERNAL은 422 |
| 이수 조회 | 평가 전에는 404 COMPLETION_NOT_FOUND |

---

## 1층 · 기본 흐름

### 1. 인증

<!-- api-step operationId=signupLocalAccount method=POST path=/auth/signup success=201 request=yes store=data.session.userId role=learner policy=공개 -->

#### 1 · 회원가입

`POST /api/v1/auth/signup` → `201`

- **호출 주체** 학습자
- **인증** 불필요
- **인가 규칙** `공개`
- **필요** 없음
- **얻음** `{userId}` = `data.session.userId`, `{accessToken}` = `data.session.userId`

응답이 login과 같은 AuthTokenResponse입니다 — data.accessToken이 이미 들어 있어 가입 직후 바로 보호 API를 호출할 수 있습니다.

```bash
curl -X POST '{baseUrl}/api/v1/auth/signup' -H 'Content-Type: application/json' -d '{"email":"learner@example.com","password":"{password}","name":"홍길동"}'
```

<!-- api-step operationId=loginLocalAccount method=POST path=/auth/login success=200 request=yes store=data.accessToken role=learner policy=공개 -->

#### 2 · 로그인

`POST /api/v1/auth/login` → `200`

- **호출 주체** 학습자
- **인증** 불필요
- **인가 규칙** `공개`
- **필요** 없음
- **얻음** `{accessToken}` = `data.accessToken`

가입 직후에는 필요 없습니다. 토큰 만료(TTL 1시간) 후 재발급용입니다.

```bash
curl -X POST '{baseUrl}/api/v1/auth/login' -H 'Content-Type: application/json' -d '{"email":"learner@example.com","password":"{password}"}'
```

<!-- api-step operationId=getAuthSession method=GET path=/auth/session success=200 request=- store=- role=learner policy=authenticated -->

#### 3 · 인증 세션 확인

`GET /api/v1/auth/session` → `200`

- **호출 주체** 학습자 (또한 가능: 강사, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `authenticated`
- **필요** 없음
- **얻음** 없음

역할은 토큰이 아니라 요청마다 DB에서 읽습니다. 권한이 바뀌면 이 응답이 즉시 따라옵니다.

```bash
curl '{baseUrl}/api/v1/auth/session' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=getCurrentUser method=GET path=/users/me success=200 request=- store=data.id role=learner policy=authenticated -->

#### 4 · 내 정보 조회

`GET /api/v1/users/me` → `200`

- **호출 주체** 학습자 (또한 가능: 강사, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `authenticated`
- **필요** 없음
- **얻음** `{userId}` = `data.id`

userId를 놓쳤을 때 다시 얻는 경로입니다.

```bash
curl '{baseUrl}/api/v1/users/me' -H 'Authorization: Bearer {accessToken}'
```

### 2. 기관

<!-- api-step operationId=listInstitutions method=GET path=/institutions success=200 request=- store=data[].id role=learner policy=authenticated -->

#### 5 · 기관 목록

`GET /api/v1/institutions?page=0&size=20&sort=createdAt,desc` → `200`

- **호출 주체** 학습자 (또한 가능: 강사, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `authenticated`
- **필요** 없음
- **얻음** `{institutionId}` = `data[].id`

인증만 되면 누구나 호출합니다. 관리자도 과정 생성 전에 institutionId를 여기서 얻습니다.

```bash
curl '{baseUrl}/api/v1/institutions?page=0&size=20&sort=createdAt,desc' -H 'Authorization: Bearer {accessToken}'
```

### 3. 강사 권한

<!-- api-step operationId=applyInstructor method=POST path=/institutions/{institutionId}/instructor-applications success=201 request=yes store=data.id role=learner policy=requireActiveContextUser -->

#### 6 · 강사 신청

`POST /api/v1/institutions/{institutionId}/instructor-applications` → `201`

- **호출 주체** 학습자
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireActiveContextUser`
- **필요** `{institutionId}`
- **얻음** `{applicationId}` = `data.id`

본인 계정이 ACTIVE여야 신청됩니다.

```bash
curl -X POST '{baseUrl}/api/v1/institutions/{institutionId}/instructor-applications' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"applicationMessage":"강사 신청 사유"}'
```

<!-- api-step operationId=listMyInstructorApplications method=GET path=/instructor-applications/me success=200 request=- store=- role=learner policy=본인 -->

#### 7 · 내 신청 상태

`GET /api/v1/instructor-applications/me?page=0&size=20` → `200`

- **호출 주체** 학습자
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `본인`
- **필요** 없음
- **얻음** 없음

data[].status — PENDING / APPROVED / REJECTED.

```bash
curl '{baseUrl}/api/v1/instructor-applications/me?page=0&size=20' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=approveInstructorApplication method=POST path=/instructor-applications/{applicationId}/approve success=200 request=- store=- role=admin policy=requireCourseManager -->

#### 8 · 관리자 승인

`POST /api/v1/instructor-applications/{applicationId}/approve` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{applicationId}`
- **얻음** 없음

PENDING 신청만 승인됩니다. 이미 처리된 신청은 409, 신청자가 ACTIVE가 아니어도 실패합니다. 승인 즉시 그 사람의 기존 토큰에 강사 권한이 적용됩니다.

```bash
curl -X POST '{baseUrl}/api/v1/instructor-applications/{applicationId}/approve' -H 'Authorization: Bearer {adminAccessToken}'
```

<!-- api-step operationId=loginLocalAccount method=POST path=/auth/login success=200 request=yes store=- role=learner policy=공개 -->

#### 선택 · 재로그인 (불필요)

`POST /api/v1/auth/login` → `200`

- **호출 주체** 학습자
- **인증** 불필요
- **인가 규칙** `공개`
- **필요** 없음
- **얻음** 없음

> **주의** 흔한 오해입니다. 역할은 JWT에 들어 있지 않고 요청마다 DB에서 읽습니다. 승인 직후 기존 accessToken 그대로 강사 API가 통과하므로 재로그인할 필요가 없습니다.

토큰을 새로 받아도 되지만 얻는 것은 없습니다. 만료된 토큰을 갱신할 때만 의미가 있습니다.

```bash
curl -X POST '{baseUrl}/api/v1/auth/login' -H 'Content-Type: application/json' -d '{"email":"learner@example.com","password":"{password}"}'
```

<!-- api-step operationId=rejectInstructorApplication method=POST path=/instructor-applications/{applicationId}/reject success=200 request=yes store=- role=admin policy=requireCourseManager -->

#### 분기 · 강사 신청 거절

`POST /api/v1/instructor-applications/{applicationId}/reject` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{applicationId}`
- **얻음** 없음

PENDING 신청만 거절됩니다(이미 처리됐으면 409). rejectionReason이 필수입니다.

```bash
curl -X POST '{baseUrl}/api/v1/instructor-applications/{applicationId}/reject' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"rejectionReason":"자격 요건 미충족"}'
```

### 4. 과정·회차

<!-- api-step operationId=createCourse method=POST path=/courses success=201 request=yes store=data.id role=admin policy=requireCourseManager -->

#### 9 · 과정 생성

`POST /api/v1/courses` → `201`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{institutionId}`
- **얻음** `{courseId}` = `data.id`

body의 institutionId가 본인이 관리하는 기관과 다르면 403 INSTITUTION_SCOPE_FORBIDDEN입니다.

```bash
curl -X POST '{baseUrl}/api/v1/courses' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"institutionId":"{institutionId}","courseCode":"WEB-2026-01","title":"웹 개발 과정","educationType":"OFFLINE","startDate":"2026-09-01","endDate":"2026-09-30","capacity":20}'
```

<!-- api-step operationId=assignCourseInstructor method=POST path=/courses/{courseId}/instructors success=201 request=yes store=- role=admin policy=requireCourseManager -->

#### 10 · 강사 배정

`POST /api/v1/courses/{courseId}/instructors` → `201`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{courseId}`, `{userId}`
- **얻음** 없음

승인된 기관 강사만 배정됩니다. 회차·출결·이수를 다루려면 MAIN이어야 합니다(ASSISTANT 불가).

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/instructors' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"userId":"{userId}","role":"MAIN"}'
```

<!-- api-step operationId=publishCourse method=POST path=/courses/{courseId}/publish success=200 request=- store=- role=admin policy=requireCourseManager -->

#### 11 · 모집 공개

`POST /api/v1/courses/{courseId}/publish` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{courseId}`
- **얻음** 없음

DRAFT → RECRUITING. 수강신청은 RECRUITING에서만 받습니다 — 공개 전 신청은 409 COURSE_STATUS_CONFLICT.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/publish' -H 'Authorization: Bearer {adminAccessToken}'
```

<!-- api-step operationId=closeCourse method=POST path=/courses/{courseId}/close success=200 request=- store=- role=admin policy=requireCourseManager -->

#### 분기 · 모집 종료

`POST /api/v1/courses/{courseId}/close` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{courseId}`
- **얻음** 없음

RECRUITING → RECRUITMENT_CLOSED. 이후 신규 신청은 409이고 이미 승인된 수강은 그대로입니다.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/close' -H 'Authorization: Bearer {adminAccessToken}'
```

<!-- api-step operationId=reopenCourse method=POST path=/courses/{courseId}/reopen success=200 request=yes store=- role=admin policy=requirePlatformAdmin -->

#### 정정 · 과정 모집 재개

`POST /api/v1/courses/{courseId}/reopen` → `200`

- **호출 주체** 플랫폼관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requirePlatformAdmin`
- **필요** `{courseId}`, 사유
- **얻음** 없음

잘못 마감한 모집을 되돌립니다. 사유가 필수이며 정정 이력이 남습니다. 종료·취소된 과정은 409입니다.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/reopen' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"reason":"모집 마감 착오"}'
```

<!-- api-step operationId=reopenCourseSession method=POST path=/sessions/{sessionId}/reopen success=200 request=yes store=- role=admin policy=requirePlatformAdmin -->

#### 정정 · 회차 재개

`POST /api/v1/sessions/{sessionId}/reopen` → `200`

- **호출 주체** 플랫폼관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requirePlatformAdmin`
- **필요** `{sessionId}`, 사유
- **얻음** 없음

마감한 회차를 다시 열어 출결을 정정합니다. `COMPLETED` 회차에서만 가능합니다.

```bash
curl -X POST '{baseUrl}/api/v1/sessions/{sessionId}/reopen' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"reason":"출결 누락 정정"}'
```

<!-- api-step operationId=revertCompletionConfirmation method=POST path=/enrollments/{enrollmentId}/completion/revert success=200 request=yes store=- role=admin policy=requirePlatformAdmin -->

#### 정정 · 이수 확정 취소

`POST /api/v1/enrollments/{enrollmentId}/completion/revert` → `200`

- **호출 주체** 플랫폼관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requirePlatformAdmin`
- **필요** `{enrollmentId}`, 사유
- **얻음** 없음

확정된 이수를 `ELIGIBLE` 로 되돌립니다. **수료증이 이미 발급된 이수는 409로 거부합니다** — 원장 기록은 지울 수 없어 DB 와 어긋나기 때문입니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/completion/revert' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"reason":"이수 확정 착오"}'
```

<!-- api-step operationId=createCourseSession method=POST path=/courses/{courseId}/sessions success=201 request=yes store=data.id role=instructor policy=requireCourseSessionManager -->

#### 12 · 회차 생성

`POST /api/v1/courses/{courseId}/sessions` → `201`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCourseSessionManager`
- **필요** `{courseId}`
- **얻음** `{sessionId}` = `data.id`

MAIN 강사 또는 기관관리자만 가능합니다. 생성 직후 상태는 SCHEDULED입니다 — 이대로는 출결을 못 엽니다.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/sessions' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"sessionNo":1,"startsAt":"2026-09-01T09:00:00+09:00","endsAt":"2026-09-01T12:00:00+09:00"}'
```

<!-- api-step operationId=updateCourseSession method=PUT path=/sessions/{sessionId} success=200 request=yes store=- role=instructor policy=requireCourseSessionManager -->

#### 13 · 회차 OPEN 전환

`PUT /api/v1/sessions/{sessionId}` → `200`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCourseSessionManager`
- **필요** `{sessionId}`
- **얻음** 없음

SCHEDULED → OPEN. 이 단계를 빠뜨리면 QR 토큰 발급이 409 ATTENDANCE_CONFLICT로 막힙니다.

```bash
curl -X PUT '{baseUrl}/api/v1/sessions/{sessionId}' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"status":"OPEN"}'
```

### 5. 수강신청

<!-- api-step operationId=createEnrollment method=POST path=/courses/{courseId}/enrollments success=201 request=yes store=data.id role=learner policy=requireContextUser -->

#### 14 · 수강신청

`POST /api/v1/courses/{courseId}/enrollments` → `201`

- **호출 주체** 학습자
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireContextUser`
- **필요** `{courseId}`, `{userId}`
- **얻음** `{enrollmentId}` = `data.id`

본인만 신청합니다. 정원이 차면 실패가 아니라 WAITLISTED로 접수되고 201이 나갑니다 — data.status를 반드시 확인하세요.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/enrollments' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"userId":"{userId}","applicationType":"SELF"}'
```

<!-- api-step operationId=createProxyEnrollment method=POST path=/courses/{courseId}/proxy-enrollments success=201 request=yes store=data.id role=admin policy=requireCourseManager -->

#### 분기 · 대리 수강신청

`POST /api/v1/courses/{courseId}/proxy-enrollments` → `201`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCourseManager`
- **필요** `{courseId}`, `{userId}`
- **얻음** `{enrollmentId}` = `data.id`

관리자 전용입니다(MAIN 강사 불가). 이후 흐름은 본인 신청과 같습니다.

```bash
curl -X POST '{baseUrl}/api/v1/courses/{courseId}/proxy-enrollments' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"userId":"{userId}"}'
```

<!-- api-step operationId=getEnrollment method=GET path=/enrollments/{enrollmentId} success=200 request=- store=- role=learner policy=requireEnrollmentSubjectOrReader -->

#### 15 · 신청 조회

`GET /api/v1/enrollments/{enrollmentId}` → `200`

- **호출 주체** 학습자 (또한 가능: 강사, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentSubjectOrReader`
- **필요** `{enrollmentId}`
- **얻음** 없음

본인 외에 관리자와 그 과정에 배정된 강사도 조회합니다(MAIN 아니어도 됨).

```bash
curl '{baseUrl}/api/v1/enrollments/{enrollmentId}' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=approveEnrollment method=POST path=/enrollments/{enrollmentId}/approve success=200 request=- store=- role=admin policy=requireEnrollmentDecisionManager -->

#### 16 · 신청 승인

`POST /api/v1/enrollments/{enrollmentId}/approve` → `200`

- **호출 주체** 기관관리자 (또한 가능: 강사)
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireEnrollmentDecisionManager`
- **필요** `{enrollmentId}`
- **얻음** 없음

APPLIED 또는 WAITLISTED에서만 승인됩니다. MAIN 강사도 승인할 수 있습니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/approve' -H 'Authorization: Bearer {adminAccessToken}'
```

<!-- api-step operationId=rejectEnrollment method=POST path=/enrollments/{enrollmentId}/reject success=200 request=yes store=- role=admin policy=requireEnrollmentDecisionManager -->

#### 분기 · 신청 반려

`POST /api/v1/enrollments/{enrollmentId}/reject` → `200`

- **호출 주체** 기관관리자 (또한 가능: 강사)
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireEnrollmentDecisionManager`
- **필요** `{enrollmentId}`
- **얻음** 없음

APPLIED · WAITLISTED에서만 가능합니다. reason 필수이고 MAIN 강사도 호출할 수 있습니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/reject' -H 'Authorization: Bearer {adminAccessToken}' -H 'Content-Type: application/json' -d '{"reason":"정원 초과"}'
```

<!-- api-step operationId=cancelEnrollment method=POST path=/enrollments/{enrollmentId}/cancel success=200 request=- store=- role=learner policy=requireEnrollmentSubjectOrDecisionManager -->

#### 분기 · 신청 취소

`POST /api/v1/enrollments/{enrollmentId}/cancel` → `200`

- **호출 주체** 학습자 (또한 가능: 기관관리자, 강사)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentSubjectOrDecisionManager`
- **필요** `{enrollmentId}`
- **얻음** 없음

APPLIED · WAITLISTED에서만 가능합니다 — 승인 전 단계입니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/cancel' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=withdrawEnrollment method=POST path=/enrollments/{enrollmentId}/withdraw success=200 request=- store=- role=learner policy=requireEnrollmentSubjectOrDecisionManager -->

#### 분기 · 수강 철회

`POST /api/v1/enrollments/{enrollmentId}/withdraw` → `200`

- **호출 주체** 학습자 (또한 가능: 기관관리자, 강사)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentSubjectOrDecisionManager`
- **필요** `{enrollmentId}`
- **얻음** 없음

APPROVED에서만 가능합니다 — 취소와 시점이 정확히 갈립니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/withdraw' -H 'Authorization: Bearer {accessToken}'
```

### 6. 출결

<!-- api-step operationId=issueAttendanceQrToken method=POST path=/sessions/{sessionId}/qr-token success=201 request=- store=data.token role=instructor policy=requireCourseSessionManager -->

#### 17 · QR 토큰 발급

`POST /api/v1/sessions/{sessionId}/qr-token` → `201`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCourseSessionManager`
- **필요** `{sessionId}`
- **얻음** `{qrToken}` = `data.token`

> **주의** 전제조건이 둘입니다. 회차가 OPEN이 아니면 409 ATTENDANCE_CONFLICT, 현재 시각이 출결창(attendanceOpensAt~attendanceClosesAt, 미설정 시 startsAt~endsAt) 밖이면 409 ATTENDANCE_WINDOW_CLOSED. 토큰 TTL은 기본 60초(`dabaeum.attendance.qr-token.ttl`로 조정 가능)라 화면에서 주기적으로 재발급해야 합니다.

HMAC 서명된 단기 토큰입니다. QR로 그려 학습자에게 노출합니다.

```bash
curl -X POST '{baseUrl}/api/v1/sessions/{sessionId}/qr-token' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=recordAttendance method=POST path=/sessions/{sessionId}/attendance success=201 request=yes store=data.id role=learner policy=본인_확인(QR)_/_requireCourseSessionManager(수동) -->

#### 18 · 출결 등록

`POST /api/v1/sessions/{sessionId}/attendance` → `201`

- **호출 주체** 학습자
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `본인 확인(QR) / requireCourseSessionManager(수동)`
- **필요** `{sessionId}`, `{enrollmentId}`, `{qrToken}`
- **얻음** `{attendanceId}` = `data.id`

> **주의** attendanceMethod가 호출 주체를 정합니다. QR이면 토큰 소유자 본인만 가능하고 강사·관리자가 대신 찍어줄 수 없습니다(403). 수동 입력은 관리자·MAIN 강사만 가능하고 학습자는 불가입니다. EXTERNAL은 422. 그 전에 enrollment가 이 회차와 같은 과정의 APPROVED 상태여야 하고(아니면 403/409), 회차·수강신청당 1건만 등록됩니다(중복 409).

강사가 만든 qrToken을 학습자 본인이 되돌려 보내는 구조입니다.

```bash
curl -X POST '{baseUrl}/api/v1/sessions/{sessionId}/attendance' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"enrollmentId":"{enrollmentId}","attendanceMethod":"QR","status":"PRESENT","source":"APP","qrToken":"{qrToken}"}'
```

<!-- api-step operationId=adjustAttendance method=PATCH path=/attendance/{attendanceId} success=200 request=yes store=- role=instructor policy=requireCourseSessionManager -->

#### 19 · 출결 보정

`PATCH /api/v1/attendance/{attendanceId}` → `200`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCourseSessionManager`
- **필요** `{attendanceId}`
- **얻음** 없음

원본을 지우지 않고 보정 이력을 남깁니다. reason 필수이고, 현재와 같은 status로는 보정할 수 없습니다(422).

```bash
curl -X PATCH '{baseUrl}/api/v1/attendance/{attendanceId}' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"status":"EXCUSED","reason":"관리자 확인 후 공결 처리"}'
```

### 7. 이수

<!-- api-step operationId=getAttendanceSummary method=GET path=/enrollments/{enrollmentId}/attendance-summary success=200 request=- store=data.attendanceRate role=instructor policy=requireEnrollmentSubjectOrReader -->

#### 20 · 출결 요약 조회

`GET /api/v1/enrollments/{enrollmentId}/attendance-summary` → `200`

- **호출 주체** 강사 (또한 가능: 학습자, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentSubjectOrReader`
- **필요** `{enrollmentId}`
- **얻음** `{attendanceRate}` = `data.attendanceRate`

> **주의** 이 응답에는 attendanceRate만 있고 completedMinutes가 없습니다. 그런데 다음 단계인 이수 평가는 completedMinutes를 정확히 요구합니다 — 현재 API로는 이 값을 받아올 방법이 없습니다. 아래 이수 평가 카드를 먼저 읽으세요.

응답 필드는 enrollmentId · totalSessions · presentCount · lateCount · absentCount · excusedCount · attendanceRate 입니다.

```bash
curl '{baseUrl}/api/v1/enrollments/{enrollmentId}/attendance-summary' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=evaluateCompletion method=POST path=/enrollments/{enrollmentId}/completion/evaluate success=200 request=yes store=data.id role=instructor policy=requireEnrollmentDecisionManager -->

#### 21 · 이수 평가

`POST /api/v1/enrollments/{enrollmentId}/completion/evaluate` → `200`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentDecisionManager`
- **필요** `{enrollmentId}`, `{attendanceRate}`
- **얻음** `{completionId}` = `data.id`

> **주의** completedMinutes를 받아올 API가 없습니다. 서버는 출결 대상 회차 중 PRESENT·LATE인 회차의 (endsAt − startsAt) 분을 합산해 계산하고, 요청 값이 다르면 409 COMPLETION_METRICS_CONFLICT를 던집니다. 그런데 이 값은 어떤 응답에도 노출되지 않아 클라이언트가 회차 목록과 출결 목록으로 직접 재계산해야 합니다. attendanceRate도 마찬가지로 정확히 일치해야 하고, 학점은행 과정이면 creditValue까지 과정 값과 같아야 합니다.

수강이 APPROVED이고, 과정의 모든 회차가 COMPLETED이거나 출결 마감 시각이 지나야 평가할 수 있습니다. 응답 status가 ELIGIBLE인지 확인하세요.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/completion/evaluate' -H 'Authorization: Bearer {accessToken}' -H 'Content-Type: application/json' -d '{"attendanceRate":"{attendanceRate}","completedMinutes":"{completedMinutes}"}'
```

<!-- api-step operationId=confirmCompletion method=POST path=/enrollments/{enrollmentId}/completion/confirm success=200 request=- store=- role=instructor policy=requireEnrollmentDecisionManager -->

#### 22 · 이수 확정

`POST /api/v1/enrollments/{enrollmentId}/completion/confirm` → `200`

- **호출 주체** 강사 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentDecisionManager`
- **필요** `{enrollmentId}`
- **얻음** 없음

평가가 ELIGIBLE이고 확정 시점에도 수강이 APPROVED여야 합니다. 성공하면 COMPLETED가 되고 Credential 발급 자격이 생깁니다.

```bash
curl -X POST '{baseUrl}/api/v1/enrollments/{enrollmentId}/completion/confirm' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=getCompletion method=GET path=/enrollments/{enrollmentId}/completion success=200 request=- store=data.id role=learner policy=requireEnrollmentSubjectOrReader -->

#### 23 · 이수 조회

`GET /api/v1/enrollments/{enrollmentId}/completion` → `200`

- **호출 주체** 학습자 (또한 가능: 강사, 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireEnrollmentSubjectOrReader`
- **필요** `{enrollmentId}`
- **얻음** `{completionId}` = `data.id`

이수 행은 평가 시점에 처음 생깁니다. 평가 전에 호출하면 404 COMPLETION_NOT_FOUND입니다.

```bash
curl '{baseUrl}/api/v1/enrollments/{enrollmentId}/completion' -H 'Authorization: Bearer {accessToken}'
```

### 8. Credential

<!-- api-step operationId=issueCredential method=POST path=/completions/{completionId}/credentials success=202 request=yes store=data.id role=admin policy=requireCourseManager -->

#### 24 · 발급 요청

`POST /api/v1/completions/{completionId}/credentials` → `202`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}` · `Idempotency-Key: {issueIdempotencyKey}`
- **인가 규칙** `requireCourseManager`
- **필요** `{completionId}`
- **얻음** `{credentialId}` = `data.id`

관리자 전용입니다(MAIN 강사 불가). 202만 돌아오고 실제 발급은 원장 기록 이후 완료됩니다.

```bash
curl -X POST '{baseUrl}/api/v1/completions/{completionId}/credentials' -H 'Authorization: Bearer {adminAccessToken}' -H 'Idempotency-Key: {issueIdempotencyKey}' -H 'Content-Type: application/json' -d '{"validUntil":"2027-09-30T00:00:00Z"}'
```

<!-- api-step operationId=getCredential method=GET path=/credentials/{credentialId} success=200 request=- store=data.credentialNo role=learner policy=requireCredentialSubjectOrInstitutionReader -->

#### 25 · 상태 polling

`GET /api/v1/credentials/{credentialId}` → `200`

- **호출 주체** 학습자 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCredentialSubjectOrInstitutionReader`
- **필요** `{credentialId}`
- **얻음** `{credentialNo}` = `data.credentialNo`

Credential 주인 본인이 직접 polling할 수 있습니다. ISSUED가 될 때까지 조회하고 FAILED면 중단합니다.

응답에는 발급 근거가 된 과정 정보(`data.courseId`, `data.courseTitle`, `data.courseCode`,
`data.institutionName`)가 함께 담기므로 수료증 화면에서 어떤 과정의 발급인지 바로 표시할 수
있습니다. 같은 필드가 `GET /api/v1/users/me/credentials` 목록의 각 항목에도 들어갑니다.
이 과정 정보는 조회 응답에만 있으며 원장에 앵커되는 VC payload에는 포함되지 않습니다.

```bash
curl '{baseUrl}/api/v1/credentials/{credentialId}' -H 'Authorization: Bearer {accessToken}'
```

<!-- api-step operationId=downloadCredentialDocument method=GET path=/credentials/{credentialId}/document success=200 request=- store=- role=learner policy=requireCredentialSubjectOrInstitutionReader -->

#### 26 · VC 다운로드

`GET /api/v1/credentials/{credentialId}/document` → `200`

- **호출 주체** 학습자 (또한 가능: 기관관리자)
- **인증** `Authorization: Bearer {accessToken}`
- **인가 규칙** `requireCredentialSubjectOrInstitutionReader`
- **필요** `{credentialId}`
- **얻음** 없음

Compact JWS 문자열입니다. JSON이 아니므로 res.text()로 받습니다. 강사는 접근할 수 없습니다.

```bash
curl '{baseUrl}/api/v1/credentials/{credentialId}/document' -H 'Authorization: Bearer {accessToken}' -o credential.jwt
```

<!-- api-step operationId=verifyCredential method=POST path=/credentials/verify success=200 request=yes store=- role=public policy=공개 -->

#### 27 · 공개 검증

`POST /api/v1/credentials/verify` → `200`

- **호출 주체** 제3자
- **인증** 불필요
- **인가 규칙** `공개`
- **필요** `{credentialNo}`
- **얻음** 없음

토큰이 필요 없습니다. credentialNo와 credentialHash 중 하나만 보냅니다.

```bash
curl -X POST '{baseUrl}/api/v1/credentials/verify' -H 'Content-Type: application/json' -d '{"credentialNo":"{credentialNo}","verificationType":"API","requesterType":"INDIVIDUAL"}'
```

### 9. 폐기·재발급

<!-- api-step operationId=reissueCredential method=POST path=/credentials/{credentialId}/reissue success=202 request=yes store=data.id role=admin policy=requireCourseManager -->

#### 28 · 재발급 요청

`POST /api/v1/credentials/{credentialId}/reissue` → `202`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}` · `Idempotency-Key: {reissueIdempotencyKey}`
- **인가 규칙** `requireCourseManager`
- **필요** `{credentialId}`
- **얻음** `{reissuedCredentialId}` = `data.id`

> **주의** 재발급은 ISSUED 상태에서만 됩니다. 폐기가 반영된 뒤(REVOKED) 호출하면 반드시 409 CREDENTIAL_STATE_CONFLICT입니다 — 정정 발급은 폐기보다 먼저 하세요.

새 credentialId가 나옵니다. 기존 id와 헷갈리지 않게 별도 변수로 보관합니다.

```bash
curl -X POST '{baseUrl}/api/v1/credentials/{credentialId}/reissue' -H 'Authorization: Bearer {adminAccessToken}' -H 'Idempotency-Key: {reissueIdempotencyKey}' -H 'Content-Type: application/json' -d '{"reason":"정정 발급"}'
```

<!-- api-step operationId=getCredential method=GET path=/credentials/{credentialId} success=200 request=- store=- role=admin policy=requireCredentialSubjectOrInstitutionReader -->

#### 29 · 재발급 상태 확인

`GET /api/v1/credentials/{reissuedCredentialId}` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCredentialSubjectOrInstitutionReader`
- **필요** `{reissuedCredentialId}`
- **얻음** 없음

새 id로 ISSUED가 될 때까지 조회합니다.

```bash
curl '{baseUrl}/api/v1/credentials/{reissuedCredentialId}' -H 'Authorization: Bearer {adminAccessToken}'
```

<!-- api-step operationId=revokeCredential method=POST path=/credentials/{credentialId}/revoke success=202 request=yes store=- role=admin policy=requireCourseManager -->

#### 30 · 폐기 요청

`POST /api/v1/credentials/{credentialId}/revoke` → `202`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}` · `Idempotency-Key: {revokeIdempotencyKey}`
- **인가 규칙** `requireCourseManager`
- **필요** `{credentialId}`
- **얻음** 없음

Idempotency-Key를 같은 본문으로 재사용하면 이전 결과가 그대로 반환되고, 본문이 다르면 409 CREDENTIAL_IDEMPOTENCY_CONFLICT입니다.

```bash
curl -X POST '{baseUrl}/api/v1/credentials/{credentialId}/revoke' -H 'Authorization: Bearer {adminAccessToken}' -H 'Idempotency-Key: {revokeIdempotencyKey}' -H 'Content-Type: application/json' -d '{"reason":"발급 정보 정정"}'
```

<!-- api-step operationId=getCredential method=GET path=/credentials/{credentialId} success=200 request=- store=- role=admin policy=requireCredentialSubjectOrInstitutionReader -->

#### 31 · 폐기 상태 확인

`GET /api/v1/credentials/{credentialId}` → `200`

- **호출 주체** 기관관리자
- **인증** `Authorization: Bearer {adminAccessToken}`
- **인가 규칙** `requireCredentialSubjectOrInstitutionReader`
- **필요** `{credentialId}`
- **얻음** 없음

REVOKED가 될 때까지 조회합니다. 여기서 흐름이 끝납니다 — 이후 재발급은 불가입니다.

```bash
curl '{baseUrl}/api/v1/credentials/{credentialId}' -H 'Authorization: Bearer {adminAccessToken}'
```

---

## 2층 · 전체 API 인덱스 (78개)

흐름도에 실린 호출은 "지도" 열에 순번이 있습니다. Badge 3개는 향후 계약이라 흐름에서 제외합니다.

### Auth (5)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/auth/signup` | 일반 사용자 회원가입 | 공개 | email, password, name | `signupLocalAccount` | 1 |
| POST | `/auth/login` | 일반 사용자 로그인 | 공개 | email, password | `loginLocalAccount` | 2 |
| GET | `/auth/session` | 현재 인증 세션 조회 | Bearer | — | `getAuthSession` | 3 |
| GET | `/auth/dadaegu/config` | 다대구 QR 로그인 siteId 조회 | 공개 | — | `getDadaeguLoginConfig` | — |
| POST | `/auth/dadaegu/login` | 다대구 DID QR 로그인 | 공개 | did | `loginWithDadaegu` | — |

### User (7)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/users` | 사용자 목록 조회 | Bearer | — | `listUsers` | — |
| POST | `/users` | 사용자 생성 | Bearer | name | `createUser` | — |
| GET | `/users/me` | 내 사용자 정보 조회 | Bearer | — | `getCurrentUser` | 4 |
| PUT | `/users/me` | 내 사용자 정보 수정 | Bearer | — | `updateCurrentUser` | — |
| GET | `/users/{userId}` | 사용자 상세 조회 | Bearer | — | `getUser` | — |
| PUT | `/users/{userId}` | 사용자 수정 | Bearer | — | `updateUser` | — |
| PATCH | `/users/{userId}/status` | 사용자 상태 변경 | Bearer | status | `changeUserStatus` | — |

### Identity & Role (8)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/users/{userId}/identities` | 사용자 identity 목록 조회 | Bearer | — | `listUserIdentities` | — |
| POST | `/users/{userId}/identities` | 사용자 identity 연결 | Bearer | provider, providerSubject, verified | `linkUserIdentity` | — |
| DELETE | `/users/{userId}/identities/{identityId}` | 사용자 identity 연결 해제 | Bearer | — | `unlinkUserIdentity` | — |
| POST | `/users/me/identities/dadaegu` | 내 계정에 다대구 DID 연결(일반→통합) | Bearer | did | `linkMyDadaeguIdentity` | — |
| POST | `/users/me/identities/local` | 내 계정에 이메일·비밀번호 연결(다대구→통합) | Bearer | email, password | `linkMyLocalIdentity` | — |
| GET | `/users/{userId}/roles` | 사용자 역할 목록 조회 | Bearer | — | `listUserRoles` | — |
| POST | `/users/{userId}/roles` | 사용자 역할 부여 | Bearer | role | `assignUserRole` | — |
| DELETE | `/users/{userId}/roles/{roleId}` | 사용자 역할 회수 | Bearer | — | `revokeUserRole` | — |

### Institution (4)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/institutions` | 기관 목록 조회 | Bearer | — | `listInstitutions` | 5 |
| POST | `/institutions` | 기관 생성 | Bearer | institutionCode, name | `createInstitution` | — |
| GET | `/institutions/{institutionId}` | 기관 상세 조회 | Bearer | — | `getInstitution` | — |
| PUT | `/institutions/{institutionId}` | 기관 수정 | Bearer | — | `updateInstitution` | — |

### Instructor (10)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/institutions/{institutionId}/instructor-applications` | 기관 강사 신청 목록 조회 | Bearer | — | `listInstitutionInstructorApplications` | — |
| POST | `/institutions/{institutionId}/instructor-applications` | 기관 강사 신청 | Bearer | — | `applyInstructor` | 6 |
| GET | `/instructor-applications/me` | 내 강사 신청 목록 조회 | Bearer | — | `listMyInstructorApplications` | 7 |
| GET | `/instructor-applications/{applicationId}` | 강사 신청 상세 조회 | Bearer | — | `getInstructorApplication` | — |
| POST | `/instructor-applications/{applicationId}/approve` | 강사 신청 승인 | Bearer | — | `approveInstructorApplication` | 8 |
| POST | `/instructor-applications/{applicationId}/reject` | 강사 신청 거절 | Bearer | rejectionReason | `rejectInstructorApplication` | 곁가지 |
| GET | `/courses/{courseId}/instructors` | 과정 강사 목록 조회 | Bearer | — | `listCourseInstructors` | — |
| POST | `/courses/{courseId}/instructors` | 과정 강사 배정 | Bearer | userId, role | `assignCourseInstructor` | 10 |
| PUT | `/courses/{courseId}/instructors/{userId}` | 과정 강사 역할 변경 | Bearer | role | `updateCourseInstructor` | — |
| DELETE | `/courses/{courseId}/instructors/{userId}` | 과정 강사 배정 해제 | Bearer | — | `removeCourseInstructor` | — |

### Course (6)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/courses` | 과정 목록 조회 | Bearer | — | `listCourses` | — |
| POST | `/courses` | 과정 생성 | Bearer | institutionId, courseCode, title, educationType, startDate, endDate, capacity | `createCourse` | 9 |
| GET | `/courses/{courseId}` | 과정 상세 조회 | Bearer | — | `getCourse` | — |
| PUT | `/courses/{courseId}` | 과정 수정 | Bearer | — | `updateCourse` | — |
| POST | `/courses/{courseId}/publish` | 과정 모집 공개 | Bearer | — | `publishCourse` | 11 |
| POST | `/courses/{courseId}/close` | 과정 모집 종료 | Bearer | — | `closeCourse` | 곁가지 |
| POST | `/courses/{courseId}/reopen` | 과정 모집 재개 (정정) | Bearer | reason | `reopenCourse` | 곁가지 |
| POST | `/sessions/{sessionId}/reopen` | 회차 재개 (정정) | Bearer | reason | `reopenCourseSession` | 곁가지 |
| POST | `/enrollments/{enrollmentId}/completion/revert` | 이수 확정 취소 (정정) | Bearer | reason | `revertCompletionConfirmation` | 곁가지 |

### Course Session (4)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/courses/{courseId}/sessions` | 과정 회차 목록 조회 | Bearer | — | `listCourseSessions` | — |
| POST | `/courses/{courseId}/sessions` | 과정 회차 생성 | Bearer | sessionNo, startsAt, endsAt | `createCourseSession` | 12 |
| GET | `/sessions/{sessionId}` | 과정 회차 상세 조회 | Bearer | — | `getCourseSession` | — |
| PUT | `/sessions/{sessionId}` | 과정 회차 수정 | Bearer | — | `updateCourseSession` | 13 |

### Enrollment (8)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/courses/{courseId}/enrollments` | 과정 수강신청 목록 조회 | Bearer | — | `listCourseEnrollments` | — |
| POST | `/courses/{courseId}/enrollments` | 수강신청 생성 | Bearer | userId | `createEnrollment` | 14 |
| POST | `/courses/{courseId}/proxy-enrollments` | 대리 수강신청 생성 | Bearer | userId | `createProxyEnrollment` | 곁가지 |
| GET | `/enrollments/{enrollmentId}` | 수강신청 상세 조회 | Bearer | — | `getEnrollment` | 15 |
| POST | `/enrollments/{enrollmentId}/approve` | 수강신청 승인 | Bearer | — | `approveEnrollment` | 16 |
| POST | `/enrollments/{enrollmentId}/reject` | 수강신청 반려 | Bearer | reason | `rejectEnrollment` | 곁가지 |
| POST | `/enrollments/{enrollmentId}/cancel` | 수강신청 취소 | Bearer | — | `cancelEnrollment` | 곁가지 |
| POST | `/enrollments/{enrollmentId}/withdraw` | 수강 철회 | Bearer | — | `withdrawEnrollment` | 곁가지 |

### Attendance (6)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/sessions/{sessionId}/qr-token` | 출결 QR 토큰 발급 | Bearer | — | `issueAttendanceQrToken` | 17 |
| GET | `/sessions/{sessionId}/attendance` | 회차 출결 목록 조회 | Bearer | — | `listSessionAttendance` | — |
| POST | `/sessions/{sessionId}/attendance` | 출결 기록 등록 | Bearer | enrollmentId, attendanceMethod, status, source | `recordAttendance` | 18 |
| GET | `/attendance/{attendanceId}` | 출결 상세 조회 | Bearer | — | `getAttendance` | — |
| PATCH | `/attendance/{attendanceId}` | 출결 보정 | Bearer | status, reason | `adjustAttendance` | 19 |
| GET | `/enrollments/{enrollmentId}/attendance-summary` | 출결 요약 조회 | Bearer | — | `getAttendanceSummary` | 20 |

### Completion (3)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/enrollments/{enrollmentId}/completion` | 이수 상태 조회 | Bearer | — | `getCompletion` | 23 |
| POST | `/enrollments/{enrollmentId}/completion/evaluate` | 이수 평가 | Bearer | attendanceRate, completedMinutes | `evaluateCompletion` | 21 |
| POST | `/enrollments/{enrollmentId}/completion/confirm` | 이수 확정 | Bearer | — | `confirmCompletion` | 22 |

### Credential (9)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/completions/{completionId}/credentials` | Credential 발급 요청 | Bearer | — | `issueCredential` | 24 |
| GET | `/credentials/{credentialId}` | Credential 상세 조회 | Bearer | — | `getCredential` | 25 |
| GET | `/credentials/{credentialId}/document` | 서명된 VC 문서 다운로드 | Bearer | — | `downloadCredentialDocument` | 26 |
| GET | `/users/me/credentials` | 내 Credential 목록 조회 | Bearer | — | `listCurrentUserCredentials` | — |
| GET | `/users/{userId}/credentials` | 사용자 Credential 목록 조회 | Bearer | — | `listUserCredentials` | — |
| POST | `/credentials/{credentialId}/revoke` | Credential 폐기 | Bearer | reason | `revokeCredential` | 30 |
| POST | `/credentials/{credentialId}/reissue` | Credential 재발급 | Bearer | reason | `reissueCredential` | 28 |
| POST | `/credentials/verify` | Credential 검증 | 공개 | verificationType, requesterType | `verifyCredential` | 27 |
| GET | `/credentials/{credentialId}/verifications` | Credential 검증 이력 조회 | Bearer | — | `listCredentialVerifications` | — |

### VC Public (5)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/vc/contexts/lifelong-education/v1` | 평생교육 VC JSON-LD Context 조회 | 공개 | — | `getLifelongEducationContextV1` | — |
| GET | `/vc/vocabulary/lifelong-education/v1` | 평생교육 VC 용어 문서 조회 | 공개 | — | `getLifelongEducationVocabularyV1` | — |
| GET | `/vc/issuers/{institutionId}` | Credential 발급기관 공개키 조회 | 공개 | — | `getCredentialIssuer` | — |
| GET | `/vc/status/{credentialNo}` | Credential 공개 상태 조회 | 공개 | — | `getPublicCredentialStatus` | — |
| GET | `/vc/status-lists/{listId}` | Credential 폐기 상태 리스트 조회 | 공개 | — | `getCredentialStatusList` | — |

### Badge (3)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/credentials/{credentialId}/badges` | 학습 Badge 발급 요청 | Bearer | badgeType, badgeName | `issueLearningBadge` | 향후 계약 |
| GET | `/users/{userId}/badges` | 사용자 Badge 목록 조회 | Bearer | — | `listUserBadges` | 향후 계약 |
| GET | `/badges/{badgeId}` | 학습 Badge 상세 조회 | Bearer | — | `getLearningBadge` | 향후 계약 |

### System (1)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/system/ping` | 시스템 상태 확인 | 공개 | — | `getSystemPing` | — |

### 전용 조회 (6)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/users/me/enrollments` | 내 수강신청 목록 조회 | Bearer | — | `listMyEnrollments` | — |
| GET | `/instructors/me/courses` | 강사 배정 과정 목록 조회 | Bearer | — | `listInstructorCourses` | — |
| GET | `/instructors/me/courses/stats` | 강사 배정 과정 통계 조회 | Bearer | — | `getInstructorCourseStats` | — |
| GET | `/institutions/{institutionId}/courses` | 기관 과정 목록 조회 | Bearer | — | `listInstitutionCourses` | — |
| GET | `/institutions/{institutionId}/enrollments` | 기관 수강신청 목록 조회 | Bearer | — | `listInstitutionEnrollments` | — |
| GET | `/institutions/{institutionId}/instructors` | 기관 소속 강사 명단 조회 | Bearer | — | `listInstitutionInstructors` | — |

### Support (9)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/notices` | 공개 공지 목록 조회 | 공개 | — | `listNotices` | — |
| GET | `/notices/{noticeId}` | 공지 상세 조회 | 공개 | — | `getNotice` | — |
| GET | `/admin/notices` | 공지 관리 목록 조회 | Bearer | — | `listAdminNotices` | — |
| POST | `/admin/notices` | 공지 등록 | Bearer | title, body, audience | `createNotice` | — |
| PUT | `/admin/notices/{noticeId}` | 공지 수정 | Bearer | title, body, audience, status | `updateNotice` | — |
| DELETE | `/admin/notices/{noticeId}` | 공지 삭제 | Bearer | — | `deleteNotice` | — |
| GET | `/support/faqs` | FAQ 목록 조회 | 공개 | — | `listFaqs` | — |
| GET | `/contents/terms` | 약관·개인정보 본문 조회 | 공개 | type | `getTermsContent` | — |
| GET | `/codes` | 공통코드 목록 조회 | 공개 | group | `listCommonCodes` | — |

### Inquiry (6)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/inquiries` | 문의 등록 | Bearer | title, content | `createInquiry` | — |
| GET | `/inquiries/{inquiryId}` | 문의 상세 조회 | Bearer | — | `getInquiry` | — |
| POST | `/inquiries/{inquiryId}/reply` | 문의 답변 | Bearer | content | `replyInquiry` | — |
| GET | `/users/me/inquiries` | 내 문의 목록 조회 | Bearer | — | `listMyInquiries` | — |
| GET | `/instructors/me/inquiries` | 강사 담당 과정 문의 목록 | Bearer | — | `listInstructorInquiries` | — |
| GET | `/institutions/{institutionId}/inquiries` | 기관 문의 목록 조회 | Bearer | — | `listInstitutionInquiries` | — |

### Review · Interest · Notification (8)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/users/me/reviews` | 내 수강평 목록 조회 | Bearer | — | `listMyReviews` | — |
| GET | `/courses/{courseId}/reviews` | 과정 수강평 목록 조회 | Bearer | — | `listCourseReviews` | — |
| POST | `/courses/{courseId}/reviews` | 수강평 등록 | Bearer | rating | `createCourseReview` | — |
| GET | `/users/me/interests` | 관심 강좌 목록 조회 | Bearer | — | `listMyInterests` | — |
| POST | `/users/me/interests` | 관심 강좌 추가 | Bearer | courseId | `addCourseInterest` | — |
| DELETE | `/users/me/interests/{interestId}` | 관심 강좌 해제 | Bearer | — | `removeCourseInterest` | — |
| GET | `/users/me/notifications` | 내 알림 목록 조회 | Bearer | — | `listMyNotifications` | — |
| POST | `/users/me/notifications/{notificationId}/read` | 알림 읽음 처리 | Bearer | — | `readNotification` | — |

### Auth 확장 (4)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/auth/email/availability` | 이메일 사용 가능 여부 조회 | 공개 | email | `checkEmailAvailability` | — |
| POST | `/auth/password/reset-request` | 비밀번호 재설정 요청 | 공개 | email | `requestPasswordReset` | — |
| POST | `/auth/password/reset` | 비밀번호 재설정 확정 | 공개 | token, password | `confirmPasswordReset` | — |
| POST | `/users/{userId}/password/reset` | 관리자 비밀번호 초기화 | Bearer | — | `adminResetPassword` | — |

### 학습 현황 · 진도율 (4)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/users/me/learning-summary` | 학습 현황 요약 조회 | Bearer | — | `getLearningSummary` | — |
| GET | `/users/me/learning-courses` | 나의 강의 목록 조회 | Bearer | — | `listLearningCourses` | — |
| GET | `/instructors/me/enrollment-status` | 강사 수강 현황 조회 | Bearer | — | `listInstructorEnrollmentStatus` | — |
| GET | `/enrollments/{enrollmentId}/progress` | 수강 진도 상세 조회 | Bearer | — | `getEnrollmentProgress` | — |

### 기관 등록 신청 · 대시보드 · 메모 (8)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/institution-applications` | 기관 등록 신청 | Bearer | institutionName, representativeName, contactEmail, contactPhone | `applyInstitutionJoin` | — |
| GET | `/institution-applications` | 기관 등록 신청 목록 조회 | Bearer | — | `listInstitutionJoinApplications` | — |
| GET | `/institution-applications/{applicationId}` | 기관 등록 신청 상세 조회 | Bearer | — | `getInstitutionJoinApplication` | — |
| POST | `/institution-applications/{applicationId}/approve` | 기관 등록 신청 승인 | Bearer | — | `approveInstitutionJoinApplication` | — |
| POST | `/institution-applications/{applicationId}/reject` | 기관 등록 신청 거절 | Bearer | rejectionReason | `rejectInstitutionJoinApplication` | — |
| GET | `/admin/dashboard` | 플랫폼 대시보드 조회 | Bearer | — | `getPlatformDashboard` | — |
| GET | `/institutions/{institutionId}/dashboard` | 기관 대시보드 조회 | Bearer | — | `getInstitutionDashboard` | — |
| PATCH | `/institutions/{institutionId}/instructors/{userId}` | 기관 소속 강사 메모 수정 | Bearer | — | `updateInstitutionInstructorMemo` | — |

### 파일 (2)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| POST | `/files` | 파일 업로드 (multipart) | Bearer | file, purpose | `uploadFile` | — |
| GET | `/files/{fileId}/content` | 파일 내용 조회 | 공개 | — | `downloadFileContent` | — |

### 체인 모니터링 (3)

| 메서드 | 경로 | 설명 | 인증 | 필수 필드 | operationId | 지도 |
|---|---|---|---|---|---|---|
| GET | `/blockchain/metrics` | 체인 메트릭 조회 | Bearer | — | `getBlockchainMetrics` | — |
| GET | `/blockchain/transactions` | 체인 트랜잭션 목록 조회 | Bearer | — | `listBlockchainTransactions` | — |
| GET | `/blockchain/alerts` | 체인 이상 징후 목록 조회 | Bearer | — | `listBlockchainAlerts` | — |

---

이 문서는 `/api-guide/index.html` 의 내용을 그대로 옮긴 것입니다. HTML 쪽에는 흐름 지도, 변수 사전, 역할 필터, curl/fetch 전환, PDF 인쇄가 있습니다.
