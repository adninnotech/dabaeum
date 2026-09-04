# 다배움 백엔드

평생교육 이수 증명을 W3C Verifiable Credential 로 발급하고, 발급 사실을 원장에 앵커링하는 API 서버다.

- Java 21 / Spring Boot 4.0.7 / MyBatis / PostgreSQL 16 / Flyway
- API 계약 원본: `docs/api/dabaeum-api-v1.yaml` (빌드 시 jar 의 `META-INF/dabaeum/openapi` 로 들어간다)

## 빌드와 실행

```bash
./gradlew test          # 단위·계약 테스트
./gradlew bootJar       # 실행 가능한 jar
java -jar build/libs/dabaeum-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## 환경변수

비밀값은 저장소에 두지 않고 실행 시점에 주입한다. 아래는 값이 없으면 기동되지 않는 항목이다.

| 이름 | 쓰이는 곳 | 비고 |
|---|---|---|
| `DABAEUM_DB_USERNAME` | `application-dev.yml`, `application-local.yml` | PostgreSQL 계정 |
| `DABAEUM_DB_PASSWORD` | 〃 | |
| `DABAEUM_DEV_BEARER_TOKEN` | 개발 프로필 인증 | 개발·데모 전용 고정 토큰 |
| `DABAEUM_JWT_SIGNING_KEY` | 로컬 계정 JWT 서명 | |
| `DABAEUM_DADAEGU_SITE_ID` | 다대구 DID 로그인 | 포털에서 발급 |
| `DABAEUM_DADAEGU_PRIVATE_KEY` | 〃 | PKCS#8 Base64 한 줄 RSA 개인키 |
| `JASYPT_ENCRYPTOR_PASSWORD` | 설정값 복호화 | `ENC(...)` 설정을 쓸 때만 필요 |

선택 항목은 기본값이 있다.

| 이름 | 기본값 | 비고 |
|---|---|---|
| `DABAEUM_VC_PUBLIC_BASE_URL` | `http://127.0.0.1:8080` | VC 의 `@context`·`issuer`·상태 URL 이 이 주소를 가리킨다 |
| `DABAEUM_BLOCKCHAIN_PROVIDER` | `fake` | `fake` 는 메모리 원장, `daeguchain` 은 대구체인 Storage API |
| `DABAEUM_DAEGUCHAIN_BASE_URL` | `http://127.0.0.1:8090` | Provider 가 `daeguchain` 일 때 |
| `DABAEUM_DAEGUCHAIN_PROJECT_ID` | (없음) | 〃 |
| `DABAEUM_VC_PRIVATE_KEY_PATH` | (없음) | Ed25519 개인키. 없으면 VC 서명·상태 리스트 발급이 비활성 |
| `DABAEUM_VC_PUBLIC_KEY_PATH` | (없음) | |
| `DABAEUM_FILES_ROOT` | `./build/file-storage` | 업로드 파일 루트 |
| `DABAEUM_SSH_HOST` · `_USERNAME` · `_PASSWORD` · `_PORT` | (없음) · 22 | `local` 프로필의 DB SSH 터널 |

## 테스트

`./gradlew test` 는 외부 의존 없이 돈다.

`./gradlew integrationTest` 는 **실제 PostgreSQL 이 필요하다.** `local`·`integration-test` 프로필로
`jdbc:postgresql://127.0.0.1:15432/dabaeum_dev` 에 붙으므로, DB 와 위의 `DABAEUM_DB_*` 값이 없으면
컨텍스트 로딩에서 실패한다. `./gradlew check` 는 이 둘을 모두 실행한다.

## 원장 연동

`dabaeum.blockchain.provider=daeguchain` 이면 대구체인 Storage API(또는 같은 계약의 에뮬레이터)에
REST 로 붙는다. 백엔드에는 Fabric SDK 의존성이 없다.

원장에는 개인정보를 올리지 않는다. 키 `DCSTORE:<16자>` 아래 `스키마버전|상태|VC해시|epochMillis`
형식의 값 하나만 기록하고, VC 원문과 이수 정보는 데이터베이스에만 둔다.

폐기 상태는 W3C Bitstring Status List v1.0 으로 공개한다. 검증자는 리스트 하나를 받아
`statusListIndex` 번째 비트만 확인하므로, 어느 수료증을 조회했는지 발급기관에 드러나지 않는다.
