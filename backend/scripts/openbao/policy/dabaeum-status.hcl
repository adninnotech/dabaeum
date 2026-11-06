# 상태(Status List) 역할. education-status 의 서명과 키 메타데이터 조회만. (설계서 §13.3)
path "education-status/sign/+" {
  capabilities = ["update"]
}

path "education-status/keys/+" {
  capabilities = ["read"]
}
