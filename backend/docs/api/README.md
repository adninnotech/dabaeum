# 다배움 프론트엔드 API 문서

프론트엔드 연동의 단일 기준은 [dabaeum-api-v1.yaml](./dabaeum-api-v1.yaml)입니다.
Swagger UI에서는 OpenAPI operation의 `tags` 기준으로 기능별 그룹을 확인할 수 있습니다.

## 확인 위치

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 호출 순서 가이드: `http://localhost:8080/api-guide/index.html`
- Markdown 다운로드: `http://localhost:8080/api-guide/dabaeum-frontend-api-flow.md`
- Markdown 원본: `docs/api/dabaeum-frontend-api-flow.md`
- 정적 OpenAPI 계약: `docs/api/dabaeum-api-v1.yaml`
- API base path: `/api/v1`

가이드와 다운로드 endpoint는 `local`, `dev` 프로필에서만 제공됩니다. 외부 개발 서버의
도메인이나 포트가 다르면 `localhost:8080` 부분만 해당 서버 주소로 바꿉니다.

## 기능별 분류

| Swagger 그룹 | 범위 |
|---|---|
| System | 시스템 상태 확인 |
| Institution | 기관 생성·조회·수정 |
| Auth | 인증 세션 |
| User | 사용자 계정·프로필·상태 |
| Identity & Role | 사용자 identity·역할·기관 범위 |
| Course | 교육 과정 |
| Course Session | 과정 회차·일정 |
| Enrollment | 수강신청·승인 상태 |
| Attendance | QR 출결·보정·요약 |
| Completion | 이수 평가·확정 |
| Credential | Credential 발급·조회·검증 |
| Badge | 학습 Badge 발급·조회 |

## 공통 호출 규칙

- `System`의 ping을 제외한 API는 `Authorization: Bearer <JWT>`가 필요합니다.
- 페이지 조회 기본값은 `page=0`, `size=20`, `sort=createdAt,desc`입니다.
- 모든 식별자는 UUID 경로 변수입니다.
- 성공 응답은 `data`와 `meta` envelope을 사용합니다.
- 오류 응답은 `code`, `message`, `details`, `requestId`, `timestamp`를 사용합니다.
- OpenAPI에서 `Idempotency-Key`가 required인 상태 변경 API는 8~128자 요청 키를 보내야 합니다.
- 각 operation의 `summary`, `description`, 파라미터 `description`, enum, default, 응답 상태 코드는
  YAML 원본에서 확인합니다.

## 구현 범위 주의

Attendance·Completion·Credential API는 현재 구현되어 있습니다. Fabric 실제 쓰기는 별도
프로필과 승인 환경변수로 보호하며 일반 API·테스트 실행에서 자동 수행하지 않습니다. Badge
operation은 향후 계약만 문서화되어 있고 아직 실제 호출 대상이 아닙니다. 상태 변경·권한·오류
코드는 Swagger의 해당 operation 설명과 응답 목록을 기준으로 처리합니다.
