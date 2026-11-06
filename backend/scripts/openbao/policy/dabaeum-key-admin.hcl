# 키 관리 역할. 두 mount 의 생성·조회·회전과 min_encryption_version 변경만. (설계서 §13.4)
#
# 경로 우선순위: OpenBao 는 여러 규칙이 맞으면 첫 와일드카드가 더 뒤에 있는 규칙 하나만 적용하고
# 나머지의 allowed_parameters/denied_parameters 는 무시한다. 그래서 키 자체는 keys/+ (정확히 한 세그먼트),
# 하위 동작은 keys/+/동작 으로 나눈다. keys/institution-* 처럼 넓게 쓰면 keys/x/config 와
# keys/x/trim 에도 이 규칙이 이겨 파라미터 제한이 사라지고 trim 이 열린다.
#
# keys/+/trim, keys/+/backup, export/*, keys/+ 의 delete 는 규칙이 없으므로 기본 거부.

path "education-sign/keys/+" {
  capabilities = ["create", "read", "update"]
  denied_parameters = {
    "exportable"             = []
    "allow_plaintext_backup" = []
    "deletion_allowed"       = []
    "auto_rotate_period"     = []
  }
}

path "education-sign/keys/+/rotate" {
  capabilities = ["update"]
}

path "education-sign/keys/+/config" {
  capabilities = ["update"]
  allowed_parameters = {
    "min_encryption_version" = []
  }
}

path "education-status/keys/+" {
  capabilities = ["create", "read", "update"]
  denied_parameters = {
    "exportable"             = []
    "allow_plaintext_backup" = []
    "deletion_allowed"       = []
    "auto_rotate_period"     = []
  }
}

path "education-status/keys/+/rotate" {
  capabilities = ["update"]
}

path "education-status/keys/+/config" {
  capabilities = ["update"]
  allowed_parameters = {
    "min_encryption_version" = []
  }
}
