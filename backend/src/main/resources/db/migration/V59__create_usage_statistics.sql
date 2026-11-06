-- 이용 통계 (docs/specs/usage-statistics.md §7). 방문·검색·배너 클릭 원문은 1년, 일별 집계는 계속 보존한다.
-- user_id·IP·User-Agent 원문은 저장하지 않는다. 기록 순간의 회원 여부·성별·연령대만 남긴다(§3).

CREATE TABLE tb_usage_events (
    event_date  DATE         NOT NULL,
    event_type  VARCHAR(20)  NOT NULL,
    viewer_key  CHAR(64)     NOT NULL,
    target_key  VARCHAR(100) NOT NULL DEFAULT '',
    member_type VARCHAR(10)  NOT NULL,
    gender      VARCHAR(20)  NOT NULL,
    age_band    VARCHAR(10)  NOT NULL,
    zero_result BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 같은 사람·같은 대상은 하루 1건. 날짜가 앞이라 하루 집계와 보존 삭제가 PK 범위로 끝난다.
    CONSTRAINT pk_tb_usage_events PRIMARY KEY (event_date, event_type, viewer_key, target_key),
    CONSTRAINT ck_tb_usage_events_type CHECK (event_type IN ('VISIT', 'SEARCH', 'BANNER_CLICK')),
    CONSTRAINT ck_tb_usage_events_member CHECK (member_type IN ('MEMBER', 'GUEST')),
    CONSTRAINT ck_tb_usage_events_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNDISCLOSED', 'UNKNOWN')),
    CONSTRAINT ck_tb_usage_events_age CHECK (age_band IN ('10', '20', '30', '40', '50', '60', '70', 'UNKNOWN'))
);

-- 날짜 × 지표 × 대상(배너만) × 구간 → 명수. 배치가 하루 단위로 지우고 다시 쓴다.
CREATE TABLE tb_usage_daily_stats (
    stat_date   DATE         NOT NULL,
    metric      VARCHAR(20)  NOT NULL,
    target_key  VARCHAR(100) NOT NULL DEFAULT '',
    member_type VARCHAR(10)  NOT NULL,
    gender      VARCHAR(20)  NOT NULL,
    age_band    VARCHAR(10)  NOT NULL,
    count       INTEGER      NOT NULL,
    CONSTRAINT pk_tb_usage_daily_stats PRIMARY KEY (stat_date, metric, target_key, member_type, gender, age_band),
    CONSTRAINT ck_tb_usage_daily_stats_metric
        CHECK (metric IN ('VISIT', 'SEARCH', 'COURSE_VIEW', 'ENROLLMENT', 'BANNER_CLICK')),
    CONSTRAINT ck_tb_usage_daily_stats_member CHECK (member_type IN ('MEMBER', 'GUEST')),
    CONSTRAINT ck_tb_usage_daily_stats_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNDISCLOSED', 'UNKNOWN')),
    CONSTRAINT ck_tb_usage_daily_stats_age
        CHECK (age_band IN ('10', '20', '30', '40', '50', '60', '70', 'UNKNOWN')),
    CONSTRAINT ck_tb_usage_daily_stats_count CHECK (count > 0)
);

-- 그날 3명 이상이 검색한 검색어만(§6.3). 그보다 적은 검색어는 원문 1년 뒤 사라진다.
CREATE TABLE tb_search_keyword_daily (
    stat_date             DATE        NOT NULL,
    keyword               VARCHAR(50) NOT NULL,
    searchers             INTEGER     NOT NULL,
    zero_result_searchers INTEGER     NOT NULL,
    CONSTRAINT pk_tb_search_keyword_daily PRIMARY KEY (stat_date, keyword),
    CONSTRAINT ck_tb_search_keyword_daily_counts
        CHECK (searchers > 0 AND zero_result_searchers >= 0 AND zero_result_searchers <= searchers)
);

-- 집계를 마친 날. 기록이 하나도 없던 날도 남아야 다음 실행이 다시 돌지 않는다.
CREATE TABLE tb_usage_aggregated_days (
    stat_date     DATE        NOT NULL,
    aggregated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_usage_aggregated_days PRIMARY KEY (stat_date)
);
