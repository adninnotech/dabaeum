-- W3C Bitstring Status List v1.0 폐기 상태 리스트.
-- 발급기관마다 리스트를 두고, VC 한 장은 리스트의 한 칸(statusListIndex)을 영구 배정받는다.
-- 인덱스는 발급 순서가 드러나지 않도록 무작위로 고르며 한 번 배정한 칸은 재사용하지 않는다.
-- 리스트 문서 자체에는 비트 배열만 담기고 이수·출결·개인정보는 들어가지 않는다.

CREATE TABLE tb_credential_status_lists (
    id UUID NOT NULL,
    institution_id UUID NOT NULL,
    list_no INTEGER NOT NULL,
    status_purpose VARCHAR(30) NOT NULL DEFAULT 'revocation',
    capacity INTEGER NOT NULL DEFAULT 131072,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_credential_status_lists PRIMARY KEY (id),
    CONSTRAINT fk_tb_credential_status_lists_institution_id
        FOREIGN KEY (institution_id) REFERENCES tb_institutions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_tb_credential_status_lists_institution_list_no
        UNIQUE (institution_id, list_no),
    CONSTRAINT ck_tb_credential_status_lists_list_no CHECK (list_no >= 1),
    CONSTRAINT ck_tb_credential_status_lists_status_purpose
        CHECK (status_purpose IN ('revocation')),
    -- 규격 최소 크기. 발급량이 적어도 리스트를 채워 herd privacy 를 확보한다.
    CONSTRAINT ck_tb_credential_status_lists_capacity CHECK (capacity >= 131072)
);

-- 전환 이전 발급분은 NULL 로 남긴다. VC 본문에 인덱스가 없어 소급 배정해도 외부 검증자가 찾을 수 없다.
ALTER TABLE tb_credentials ADD COLUMN status_list_id UUID;
ALTER TABLE tb_credentials ADD COLUMN status_list_index INTEGER;

ALTER TABLE tb_credentials
    ADD CONSTRAINT fk_tb_credentials_status_list_id
    FOREIGN KEY (status_list_id) REFERENCES tb_credential_status_lists (id) ON DELETE RESTRICT;

ALTER TABLE tb_credentials
    ADD CONSTRAINT ck_tb_credentials_status_list_entry CHECK (
        (status_list_id IS NULL) = (status_list_index IS NULL)
        AND (status_list_index IS NULL OR status_list_index >= 0)
    );

-- 같은 리스트 안에서 한 칸은 한 장에만 배정된다. NULL 은 여러 행이 가질 수 있다.
ALTER TABLE tb_credentials
    ADD CONSTRAINT uq_tb_credentials_status_list_entry UNIQUE (status_list_id, status_list_index);
