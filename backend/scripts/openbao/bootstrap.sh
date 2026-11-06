#!/usr/bin/env bash
# OpenBao 초기 구성 — mount 2개, Policy 3개, AppRole 3개. 여러 번 실행해도 같은 결과다.
# 설계서 §5.1, §13.1, §13.3, §13.4. 값(Secret ID 등)은 출력하지 않는다.
#
#   BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=<초기 구성 권한 토큰> bash scripts/openbao/bootstrap.sh
set -euo pipefail

: "${BAO_ADDR:?BAO_ADDR is required}"
: "${BAO_TOKEN:?BAO_TOKEN is required}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mount_enabled() {
    bao secrets list -format=json | grep -q "\"$1/\""
}

for mount in education-sign education-status; do
    if mount_enabled "$mount"; then
        echo "mount $mount: 있음"
    else
        bao secrets enable -path="$mount" transit
        echo "mount $mount: 생성"
    fi
    # 알 수 없는 키가 encrypt 경로에서 자동 생성되는 것을 막는다.
    bao write "$mount/config/keys" disable_upsert=true >/dev/null
done

for policy in dabaeum-signing dabaeum-status dabaeum-key-admin; do
    bao policy write "$policy" "$DIR/policy/$policy.hcl"
done

if bao auth list -format=json | grep -q '"approle/"'; then
    echo "auth approle: 있음"
else
    bao auth enable approle
    echo "auth approle: 생성"
fi
# mount 기본 상한이 768h(32일)라 role 의 secret_id_ttl 960h 가 32일로 잘린다. 상한을 함께 올린다.
bao auth tune -max-lease-ttl=960h approle/ >/dev/null

# 백엔드와 OpenBao 가 같은 호스트다. 토큰과 Secret ID 를 localhost 에 묶는다. (§13.1)
for role in dabaeum-signing dabaeum-status dabaeum-key-admin; do
    bao write "auth/approle/role/$role" \
        token_policies="$role" \
        token_ttl=15m token_max_ttl=1h \
        secret_id_ttl=960h secret_id_num_uses=0 \
        token_bound_cidrs=127.0.0.1/32 secret_id_bound_cidrs=127.0.0.1/32 >/dev/null
    echo "approle $role: 준비"
done

cat <<'EOF'

완료. Role ID 와 Secret ID 는 다음으로 발급한다 (값을 파일이나 로그에 남기지 않는다):
  bao read -field=role_id auth/approle/role/<role>/role-id
  bao write -f -field=secret_id auth/approle/role/<role>/secret-id
EOF
