#!/usr/bin/env bash
# 설계서 §19 1단계 중 OpenBao 만으로 확인할 수 있는 항목(1~7, 18)을 실측한다.
# 앱 동작이 필요한 항목(8~17)은 계획 2~N 의 테스트가 담당한다.
#
#   BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=<관리 토큰> bash scripts/openbao/poc-check.sh
#
# 관리 토큰으로 세 AppRole 에 로그인해 역할별 토큰을 얻는다. 토큰·Secret ID 는 출력하지 않는다.
set -uo pipefail

: "${BAO_ADDR:?BAO_ADDR is required}"
: "${BAO_TOKEN:?BAO_TOKEN is required}"

SIGN=education-sign
STATUS=education-status
KEY=poc-ed25519
pass=0
fail=0

check() {   # 성공해야 PASS
    local name="$1"; shift
    if "$@" >/dev/null 2>&1; then echo "PASS  $name"; pass=$((pass + 1)); else echo "FAIL  $name"; fail=$((fail + 1)); fi
}
deny() {    # 거부돼야 PASS
    local name="$1"; shift
    if "$@" >/dev/null 2>&1; then echo "FAIL  $name (허용됨)"; fail=$((fail + 1)); else echo "PASS  $name (거부됨)"; pass=$((pass + 1)); fi
}
deny_auth() {    # 권한 거부(403/permission denied)여야 PASS
    local name="$1" output rc; shift
    output="$("$@" 2>&1)"; rc=$?
    if [ "$rc" -eq 0 ]; then
        echo "FAIL  $name (허용됨)"; fail=$((fail + 1))
    elif printf '%s' "$output" | grep -Eiq '(^|[^0-9])403([^0-9]|$)|permission denied|not authorized|unauthorized'; then
        echo "PASS  $name (거부됨)"; pass=$((pass + 1))
    else
        echo "FAIL  $name (권한 거부 신호 없음)"; fail=$((fail + 1))
    fi
}
login() {
    local role_id secret_id
    role_id="$(bao read -field=role_id "auth/approle/role/$1/role-id")"
    secret_id="$(bao write -f -field=secret_id "auth/approle/role/$1/secret-id")"
    bao write -field=token auth/approle/login role_id="$role_id" secret_id="$secret_id"
}
field() {   # $1 토큰, $2 경로, $3 필드
    env BAO_TOKEN="$1" bao read -field="$3" "$2"
}
sign() {    # $1 토큰, $2 key_version → 서명 문자열
    env BAO_TOKEN="$1" bao write -field=signature "$SIGN/sign/$KEY" input="$INPUT" key_version="$2"
}

ADMIN_T="$(login dabaeum-key-admin)"
SIGN_T="$(login dabaeum-signing)"
STATUS_T="$(login dabaeum-status)"

for token_name in ADMIN_T SIGN_T STATUS_T; do
    if [ -z "${!token_name}" ]; then
        echo "FAIL  $token_name AppRole login returned an empty token" >&2
        exit 1
    fi
done

# check/deny 가 bash -c 로 여는 서브셸에서도 함수·변수를 쓸 수 있게 내보낸다.
# INPUT 은 아래에서 대입되지만 export 표시는 유지된다.
export SIGN STATUS KEY INPUT
export -f field sign

# JWS signing input 형태의 입력: base64url(header).base64url(payload) 를 그대로 base64 한 값
INPUT="$(printf '%s' 'eyJhbGciOiJFZERTQSJ9.eyJoZWxsbyI6Im9wZW5iYW8ifQ' | base64 | tr -d '\n')"

if ! env BAO_TOKEN="$ADMIN_T" bao write -f "$STATUS/keys/$KEY" type=ed25519 >/dev/null 2>&1; then
    echo "FAIL  status mount PoC key setup" >&2
    exit 1
fi
if ! env BAO_TOKEN="$STATUS_T" bao write -field=signature "$STATUS/sign/$KEY" input="$INPUT" key_version=1 >/dev/null 2>&1; then
    echo "FAIL  status role signing precondition" >&2
    exit 1
fi

echo "== 4. 관리 정책 허용"
check "4 키 생성 (파라미터 없이)"        env BAO_TOKEN="$ADMIN_T" bao write -f "$SIGN/keys/$KEY" type=ed25519
check "4 min_encryption_version 변경"    env BAO_TOKEN="$ADMIN_T" bao write "$SIGN/keys/$KEY/config" min_encryption_version=1

echo "== 7. 생성 후 상태"
check "7 type=ed25519"               bash -c "[ \"\$(field '$ADMIN_T' '$SIGN/keys/$KEY' type)\" = ed25519 ]"
check "7 exportable=false"           bash -c "[ \"\$(field '$ADMIN_T' '$SIGN/keys/$KEY' exportable)\" = false ]"
check "7 allow_plaintext_backup=false" bash -c "[ \"\$(field '$ADMIN_T' '$SIGN/keys/$KEY' allow_plaintext_backup)\" = false ]"
check "7 deletion_allowed=false"     bash -c "[ \"\$(field '$ADMIN_T' '$SIGN/keys/$KEY' deletion_allowed)\" = false ]"
check "7 derived=false"              bash -c "[ \"\$(field '$ADMIN_T' '$SIGN/keys/$KEY' derived)\" = false ]"
check "7 auto_rotate_period=0"       bash -c "field '$ADMIN_T' '$SIGN/keys/$KEY' auto_rotate_period | grep -qxE '0|0s'"

echo "== 1~3. 서명"
check "1 key_version 지정, prehashed 없이 서명" bash -c "sign '$SIGN_T' 1 | grep -q ."
S1="$(sign "$SIGN_T" 1)"; S2="$(sign "$SIGN_T" 1)"
check "2 같은 입력·버전 두 번 → 같은 서명"  test "$S1" = "$S2"
check "3 vault:v1: 접두"                 bash -c "printf '%s' '$S1' | grep -q '^vault:v1:'"
check "1 prehashed/hash_algorithm 요청 파라미터 수용" env BAO_TOKEN="$SIGN_T" bao write "$SIGN/sign/$KEY" input="$INPUT" prehashed=true hash_algorithm=sha2-256

echo "== 4. 회전 뒤에도 옛 버전 지정 서명은 min_encryption_version 이하면 허용"
check "4 rotate"                          env BAO_TOKEN="$ADMIN_T" bao write -f "$SIGN/keys/$KEY/rotate"
check "4 key_version=1 서명 (min=1)"     bash -c "sign '$SIGN_T' 1 | grep -q '^vault:v1:'"
check "4 min_encryption_version=2 로 올림" env BAO_TOKEN="$ADMIN_T" bao write "$SIGN/keys/$KEY/config" min_encryption_version=2
deny  "4 key_version=1 서명 (min=2) 거부" bash -c "sign '$SIGN_T' 1 | grep -q ."

echo "== 5. 관리 정책 거부"
deny_auth "5 min_decryption_version 변경"    env BAO_TOKEN="$ADMIN_T" bao write "$SIGN/keys/$KEY/config" min_decryption_version=1
deny_auth "5 exportable=false 를 명시한 생성" env BAO_TOKEN="$ADMIN_T" bao write -f "$SIGN/keys/$KEY-x" type=ed25519 exportable=false
deny_auth "5 auto_rotate_period 를 준 생성"   env BAO_TOKEN="$ADMIN_T" bao write -f "$SIGN/keys/$KEY-y" type=ed25519 auto_rotate_period=24h
deny_auth "5 trim"                           env BAO_TOKEN="$ADMIN_T" bao write "$SIGN/keys/$KEY/trim" min_available_version=1
deny_auth "5 키 삭제"                        env BAO_TOKEN="$ADMIN_T" bao delete "$SIGN/keys/$KEY"

echo "== 6. 역할 분리"
deny_auth "6 발급 토큰으로 rotate"           env BAO_TOKEN="$SIGN_T" bao write -f "$SIGN/keys/$KEY/rotate"
deny_auth "6 발급 토큰으로 status mount 서명" env BAO_TOKEN="$SIGN_T" bao write "$STATUS/sign/$KEY" input="$INPUT" key_version=1
deny_auth "6 상태 토큰으로 sign mount 서명"   env BAO_TOKEN="$STATUS_T" bao write "$SIGN/sign/$KEY" input="$INPUT" key_version=2

echo "== 18. 리스너 설정 (운영 대기 기준 산정용. 판정 근거가 아니다)"
bao read -format=json sys/config/state/sanitized 2>/dev/null \
    | grep -oE '"max_request_duration": *"[^"]*"' | head -1 || echo "max_request_duration: (조회 불가 — 설정 파일에서 확인)"

echo
echo "PASS $pass  FAIL $fail"
[ "$fail" -eq 0 ]
