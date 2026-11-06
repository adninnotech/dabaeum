# 발급 역할. education-sign 의 서명과 키 메타데이터 조회만. (설계서 §13.3)
# + 는 정확히 한 세그먼트라 keys/x/config 같은 하위 경로에 걸리지 않는다.
path "education-sign/sign/+" {
  capabilities = ["update"]
}

path "education-sign/keys/+" {
  capabilities = ["read"]
}
