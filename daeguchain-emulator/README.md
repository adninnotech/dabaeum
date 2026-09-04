# daeguchain-emulator

다배움 백엔드에서 Fabric 연동을 떼어낸 원장 모듈이다. 백엔드는 이 모듈과 REST 로만 통신하고,
Fabric SDK·인증 자료·SSH 터널은 전부 이 모듈 안에 있다. 대구체인 Storage API 규격이 확정되면
이 모듈이 그 계약으로 응답하도록 바꾸고, 그 뒤 실제 대구체인으로 교체할 때 백엔드는 접속 URL 만
바꾼다.

```
백엔드 ──REST──▶ 이 모듈 ──gRPC──▶ Fabric peer (체인코드 continualtion-edu)
```

## 지금 상태

- **모듈 ↔ Fabric 연동**: 완료. 백엔드 Java `FabricGatewayFactory`·`DefaultFabricStorageGateway` 와
  같은 구성(identity/signer, TLS authority override, endorse → submit → commit status, qscc 블록 높이,
  쓰기 승인 가드)이다.
- **모듈 API**: 테스트용. 대구체인 Storage API 문서를 받으면 그 계약으로 교체한다.

## 실행

```bash
npm install
cp .env.example .env      # 값을 채운다. .env 는 저장소에 넣지 않는다
npm run dev               # 저장하면 자동 재시작
```

Fabric 없이 API 만 확인하려면:

```bash
FABRIC_MODE=in-memory npm run dev
```

실제 원장에 붙어 연동을 확인하려면 (쓰기 단계는 `FABRIC_WRITE_ENABLED=true` 일 때만):

```bash
npm run smoke
```

## 서버 배치

배치 위치는 `<계정>@<서버>:<배치경로>` 이고, Fabric 인증 자료
복제본은 그 아래 `auth/` 에 있다. peer 와 같은 호스트라 SSH 터널은 쓰지 않는다
(`FABRIC_TUNNEL_ENABLED=false`, 127.0.0.1:7051 로 직접 접속).

```bash
DEPLOY_HOST=<서버> DEPLOY_USER=<계정> DEPLOY_DIR=<경로> bash scripts/deploy.sh
bash scripts/deploy.sh --restart   # 위에 더해 pm2 재시작
```

서버에서 직접:

```bash
cd ~/dabaeum/daeguchain-emulator
npm run smoke                       # 원장 연동 확인 (쓰기는 FABRIC_WRITE_ENABLED=true 일 때만)
pm2 start ecosystem.config.cjs      # 상주 실행. 로그는 logs/out.log, logs/error.log
pm2 logs daeguchain-emulator
```

서버 Node 는 배포판 패키지 v18 이다. 그래서 아래를 지킨다. Node 를 22 이상으로 올리면 전부 풀 수 있다.
- Fastify 4, dotenv (`--env-file` 은 Node 20+ 전용)
- `@hyperledger/fabric-gateway` 는 `~1.11.0` 고정. 1.12 부터 `@noble/curves` 2.x(ESM 전용)를
  `require()` 해서 Node 18 의 `node dist/main.js` 가 `ERR_REQUIRE_ESM` 으로 기동되지 않는다.
  (`tsx` 로 실행하면 돌아가지만 pm2 상주 실행은 `node dist/main.js` 를 쓴다.)

개발 PC 에서 서버 peer 에 붙으려면 `.env` 에서 터널을 켜고 SSH 계정과 `known_hosts` 를 지정한다.
터널은 keepalive 를 보내고 세션이 끊기면 다음 호출에서 다시 연다.

## API — 대구체인 Storage API 호환

경로·요청·응답을 대구체인 BaaS 문서(`../대구체인-Storage-API-Project-list.md`)와 같게 맞췄다.
모든 요청은 `POST` + JSON 본문이고 `token`, `chain` 을 함께 보낸다. 응답 봉투는
`{ state, rcode, msg, data, cid }` 다. 접두는 `/daeguchain/v2/mitum/storage` 라서 백엔드는
나중에 호스트만 실제 대구체인으로 바꾸면 된다.

| 경로 | 문서 원문 | 체인코드 |
|---|---|---|
| `POST …/projects` | Project list | — (에뮬레이터 보관) |
| `POST …/regist_project` | Regist project (add / remove) | — (에뮬레이터 보관) |
| `POST …/create_data` | Create Data | CreateData |
| `POST …/update_data` | Update Data | UpdateData |
| `POST …/delete_data` | Delete Data | DeleteData |
| `POST …/get_data` | Get Data | GetData + GetDataHistory |
| `POST …/data_history` | Data History | GetDataHistory + qscc GetBlockByTxID |

쓰기 응답의 `tx.hash`·`fact_hash` 는 Fabric 트랜잭션 ID 에서 결정적으로 파생한 base58 값이고,
`receipt.height` 는 Fabric 블록 번호, `receipt.in_state` 는 커밋 확정 여부다. Fabric 이 무효로
판정하면 `in_state=false` 에 `reason` 으로 검증 코드가 들어간다. 주소(`0x…fca`)·공개키(`…fpu`)·
서명은 형식만 맞춘 파생값이며 암호학적 의미가 없다.

문서에 없는 것은 이렇게 정했다:
- 오류: 업무 오류(없는 키, 중복 키, 검증 실패, 없는 프로젝트)는 **HTTP 200 + `state: "ERROR"`**,
  `rcode: { code, http }`. 인증 실패만 401, 원장 연결·제출 실패는 502/503/504.
- Storage 프로젝트는 Fabric 에 없는 개념이라 `EMULATOR_DATA_DIR/projects.json` 에 보관한다.
  데이터 키는 프로젝트와 무관하게 Fabric 에 그대로 저장되므로 프로젝트가 달라도 같은 키는 같은
  원장 항목이다. 백엔드는 프로젝트를 하나만 쓴다.
- `data_key` 는 문서 규칙(20자 이하, 공백·`:/?#[]@` 금지)으로 검사하고 그대로 체인코드에
  넘긴다. 현재 체인코드는 `^[A-Z0-9]{16}$` 만 받으므로 그 밖의 키는 `LEDGER_SUBMIT_FAILED` 가
  된다. 백엔드 chainKey 는 이 형식이라 문제없다. `data_value` 는 200자 이하이며 형식은
  체인코드가 검사한다(백엔드 `StorageValue` = `1|A|hash|epochMillis`).
- `data_history` 의 `offset` 은 문서대로 0/미지정이면 현재 블록 번호이고, `reverse=true` 는 그보다
  작은 블록(최신 우선), `false` 는 큰 블록을 돌려준다. `limit` 은 10~50.

디버깅용 통과 경로는 남겨 두었다. 백엔드 계약이 아니다.

| 경로 | 설명 |
|---|---|
| `GET /health` | 원장 높이까지 읽혀야 `UP` |
| `POST /fabric/evaluate` `{function, arguments}` | 체인코드 함수 직접 조회. `MODULE_API_KEY` 설정 시 `X-Api-Key` 필요 |
| `POST /fabric/submit` `{function, arguments}` | 체인코드 함수 직접 제출 |

## 체인코드

`chaincode/continualtion-edu/` 는 Fabric 에 배포된 Go 체인코드 원본이다. 백엔드 저장소에서
옮겨왔다(백엔드는 이제 Fabric 을 직접 알지 못한다). 에뮬레이터는 이 체인코드의
`CreateData`·`UpdateData`·`DeleteData`·`GetData`·`GetDataHistory` 를 호출하며, 키 규칙
`^[A-Z0-9]{16}$` 과 값 규칙 `^1\|[ARS]\|[0-9a-f]{64}\|[1-9][0-9]{0,18}$` 은 여기에 정의돼 있다.
체인코드를 바꾸면 peer 에 다시 배포해야 한다(원장 변경이므로 승인이 필요하다).

## 테스트

```bash
npm test          # Fabric 없이 (메모리 원장)
npm run typecheck
```
