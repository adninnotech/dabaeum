# 다배움-프론트엔드

Vue 3 + Quasar(JavaScript) 기반 평생학습 플랫폼 프론트엔드입니다. Desktop / Mobile Web을 지원하며, 모바일에서는 PWA로 설치할 수 있습니다.

## 시작하기

```bash
# Node.js 22.12+ 권장
nvm use 22.22.0
npm install
npm run dev
```

PWA 모드:

```bash
npm run dev:pwa
```

## 문서

- API 계약: [`apitest/dabaeum-api-v1.yaml`](./apitest/dabaeum-api-v1.yaml)
- API 테스트: [`apitest/test.http`](./apitest/test.http)
- Swagger: http://<api-host>:8080/swagger-ui/index.html#/

## 주요 경로

| 경로                | 설명                   |
| ------------------- | ---------------------- |
| `/`                 | 학습자 강좌찾기 (메인) |
| `/admin`            | 관리자 대시보드        |
| `/admin/blockchain` | 블록체인 모니터링      |
