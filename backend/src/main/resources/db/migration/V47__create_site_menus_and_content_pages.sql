-- 평생학습플랫폼 고도화 계획 8 — CMS 메뉴·콘텐츠 (설계서 §7)
-- 메뉴 깊이 2단계와 route_key 허용 목록(MenuRoute)은 서비스가 검사한다. 본문 HTML 은 저장 전에 정제한다.

CREATE TABLE tb_content_pages (
    id           UUID         NOT NULL,
    slug         VARCHAR(100) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body_html    TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_by   UUID         NOT NULL,
    updated_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_content_pages PRIMARY KEY (id),
    CONSTRAINT uq_tb_content_pages_slug UNIQUE (slug),
    CONSTRAINT ck_tb_content_pages_slug CHECK (slug ~ '^[a-z0-9-]{2,100}$'),
    CONSTRAINT ck_tb_content_pages_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_tb_content_pages_published CHECK (status = 'DRAFT' OR published_at IS NOT NULL)
);

CREATE TABLE tb_site_menus (
    id              UUID         NOT NULL,
    parent_id       UUID,
    name            VARCHAR(60)  NOT NULL,
    link_type       VARCHAR(20)  NOT NULL,
    route_key       VARCHAR(100),
    content_page_id UUID,
    external_url    VARCHAR(500),
    sort_order      INTEGER      NOT NULL,
    visible         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_site_menus PRIMARY KEY (id),
    CONSTRAINT fk_tb_site_menus_parent FOREIGN KEY (parent_id) REFERENCES tb_site_menus (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tb_site_menus_content FOREIGN KEY (content_page_id) REFERENCES tb_content_pages (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_site_menus_link CHECK (
        (link_type = 'ROUTE'    AND route_key IS NOT NULL AND content_page_id IS NULL AND external_url IS NULL) OR
        (link_type = 'CONTENT'  AND content_page_id IS NOT NULL AND route_key IS NULL AND external_url IS NULL) OR
        (link_type = 'EXTERNAL' AND external_url IS NOT NULL AND route_key IS NULL AND content_page_id IS NULL)
    ),
    CONSTRAINT ck_tb_site_menus_external_url CHECK (external_url IS NULL OR external_url ~ '^https?://')
);

CREATE INDEX ix_tb_site_menus_parent ON tb_site_menus (parent_id, sort_order);
