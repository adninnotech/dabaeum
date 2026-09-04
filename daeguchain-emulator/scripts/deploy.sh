#!/usr/bin/env bash
# 개발 PC 에서 서버로 소스를 올리고 의존성을 설치한다. 실행(pm2)은 따로 한다.
#
#   bash scripts/deploy.sh            # 소스 업로드 + npm ci + build
#   bash scripts/deploy.sh --restart  # 위에 더해 pm2 재시작
#
# 서버 배치 위치와 계정은 백엔드 배포 절차와 같다. node_modules·dist·.env 는 올리지 않는다.
# 서버의 .env 가 없으면 .env.example 을 복사한다 (서버 기본값은 터널 없음·auth/ 경로라 비밀값이 없다).
set -euo pipefail

HOST="${DEPLOY_HOST:?DEPLOY_HOST 를 지정하라}"
PORT="${DEPLOY_PORT:-22}"
USER_NAME="${DEPLOY_USER:?DEPLOY_USER 를 지정하라}"
REMOTE_DIR="${DEPLOY_DIR:?DEPLOY_DIR 를 지정하라}"
RESTART=false
[[ "${1:-}" == "--restart" ]] && RESTART=true

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="$(mktemp -t daeguchain-emulator.XXXXXX.tgz)"
trap 'rm -f "$ARCHIVE"' EXIT

echo "[1/4] 소스 묶기"
tar -czf "$ARCHIVE" -C "$ROOT" \
  --exclude='node_modules' --exclude='dist' --exclude='logs' --exclude='.env' --exclude='*.log' \
  package.json package-lock.json tsconfig.json ecosystem.config.cjs README.md .env.example .gitignore \
  src test scripts

echo "[2/4] 업로드 → ${USER_NAME}@${HOST}:${REMOTE_DIR}"
ssh -p "$PORT" "${USER_NAME}@${HOST}" "mkdir -p '${REMOTE_DIR}/logs'"
scp -P "$PORT" -q "$ARCHIVE" "${USER_NAME}@${HOST}:${REMOTE_DIR}/.deploy.tgz"

echo "[3/4] 서버에서 풀고 의존성 설치·빌드"
ssh -p "$PORT" "${USER_NAME}@${HOST}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DIR}'
tar -xzf .deploy.tgz && rm -f .deploy.tgz
[[ -f .env ]] || { cp .env.example .env; echo "  .env 가 없어 .env.example 을 복사했다"; }
npm ci --no-audit --no-fund --loglevel=error
npm run build --silent
node -e "console.log('  node', process.version)"
EOF

if [[ "$RESTART" == true ]]; then
  echo "[4/4] pm2 재시작"
  ssh -p "$PORT" "${USER_NAME}@${HOST}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DIR}'
if pm2 describe daeguchain-emulator >/dev/null 2>&1; then pm2 restart daeguchain-emulator --update-env; else pm2 start ecosystem.config.cjs; fi
pm2 status daeguchain-emulator
EOF
else
  echo "[4/4] 재시작은 건너뜀. 서버에서: cd ${REMOTE_DIR} && pm2 start ecosystem.config.cjs"
fi
