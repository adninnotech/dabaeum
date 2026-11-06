-- 평생학습플랫폼 고도화 계획 3 — 강좌 분류 표준 (설계서 §12)
-- 정본: "강좌 분류체계 표준화" 시트(A1:H54, 2026-09-28). 코드는 TSIX_AABBCC, 상위 단계는 하위 자리를 00 으로 채운다.
-- 코드·이름은 표준이 정한다. 관리자는 enabled 만 바꾸고, 추가·변경은 마이그레이션으로 한다.

CREATE TABLE tb_course_classifications (
    code        VARCHAR(11)  NOT NULL,
    level       SMALLINT     NOT NULL,
    parent_code VARCHAR(11),
    name        VARCHAR(100) NOT NULL,
    sort_order  INTEGER      NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tb_course_classifications PRIMARY KEY (code),
    CONSTRAINT fk_tb_course_classifications_parent
        FOREIGN KEY (parent_code) REFERENCES tb_course_classifications (code) ON DELETE RESTRICT,
    CONSTRAINT ck_tb_course_classifications_code CHECK (code ~ '^TSIX_[0-9]{6}$'),
    CONSTRAINT ck_tb_course_classifications_level CHECK (
        (level = 1 AND parent_code IS NULL) OR (level IN (2, 3) AND parent_code IS NOT NULL))
);

CREATE INDEX ix_tb_course_classifications_parent ON tb_course_classifications (parent_code, sort_order);

-- TSIX_070000(성인진로개발역량향상교육)은 시트에 하위가 "추가 필요" 로 비어 있어 1단계만 넣는다.
INSERT INTO tb_course_classifications (code, level, parent_code, name, sort_order) VALUES
    ('TSIX_010000', 1, NULL, '기초문해교육', 1),
    ('TSIX_020000', 1, NULL, '학력보완교육', 2),
    ('TSIX_030000', 1, NULL, '직업능력교육', 3),
    ('TSIX_040000', 1, NULL, '문화예술교육', 4),
    ('TSIX_050000', 1, NULL, '인문교양교육', 5),
    ('TSIX_060000', 1, NULL, '시민참여교육', 6),
    ('TSIX_070000', 1, NULL, '성인진로개발역량향상교육', 7),
    ('TSIX_010100', 2, 'TSIX_010000', '문자해득', 1),
    ('TSIX_010200', 2, 'TSIX_010000', '생활문해', 2),
    ('TSIX_020100', 2, 'TSIX_020000', '초등학력보완', 3),
    ('TSIX_020200', 2, 'TSIX_020000', '중등학력보완', 4),
    ('TSIX_020300', 2, 'TSIX_020000', '고등학력보완', 5),
    ('TSIX_030100', 2, 'TSIX_030000', '자격인증', 6),
    ('TSIX_030200', 2, 'TSIX_030000', '직업준비', 7),
    ('TSIX_030300', 2, 'TSIX_030000', '직무역량', 8),
    ('TSIX_040100', 2, 'TSIX_040000', '레저생활스포츠', 9),
    ('TSIX_040200', 2, 'TSIX_040000', '생활문화예술', 10),
    ('TSIX_040300', 2, 'TSIX_040000', '문화예술향상', 11),
    ('TSIX_050100', 2, 'TSIX_050000', '건강심성', 12),
    ('TSIX_050200', 2, 'TSIX_050000', '기능적소양', 13),
    ('TSIX_050300', 2, 'TSIX_050000', '인문학적교양', 14),
    ('TSIX_060100', 2, 'TSIX_060000', '시민책무성', 15),
    ('TSIX_060200', 2, 'TSIX_060000', '시민리더역량', 16),
    ('TSIX_060300', 2, 'TSIX_060000', '시민참여활동', 17),
    ('TSIX_060400', 2, 'TSIX_060000', '대구시민대학', 18),
    ('TSIX_010101', 3, 'TSIX_010100', '기초한글', 1),
    ('TSIX_010102', 3, 'TSIX_010100', '내국인한글문해', 2),
    ('TSIX_010103', 3, 'TSIX_010100', '다문화한글문해', 3),
    ('TSIX_010104', 3, 'TSIX_010100', '기타한글문해', 4),
    ('TSIX_010201', 3, 'TSIX_010200', '기초컴퓨터', 5),
    ('TSIX_010202', 3, 'TSIX_010200', '스마트폰', 6),
    ('TSIX_010203', 3, 'TSIX_010200', '리터러시', 7),
    ('TSIX_010204', 3, 'TSIX_010200', '한글시화전', 8),
    ('TSIX_010205', 3, 'TSIX_010200', '기타 생활문해', 9),
    ('TSIX_020101', 3, 'TSIX_020100', '유아초등대상 연계교육', 10),
    ('TSIX_020102', 3, 'TSIX_020100', '초등학력인증', 11),
    ('TSIX_020202', 3, 'TSIX_020200', '중등학력 인증', 12),
    ('TSIX_020302', 3, 'TSIX_020300', '학점은행제', 13),
    ('TSIX_030101', 3, 'TSIX_030100', '자격증 취득과정', 14),
    ('TSIX_030102', 3, 'TSIX_030100', '지도사양성과정', 15),
    ('TSIX_030103', 3, 'TSIX_030100', '외국어 및 컴퓨터 자격인증', 16),
    ('TSIX_030201', 3, 'TSIX_030200', '취업준비', 17),
    ('TSIX_030202', 3, 'TSIX_030200', '창업과정', 18),
    ('TSIX_030203', 3, 'TSIX_030200', '재취업교육', 19),
    ('TSIX_030301', 3, 'TSIX_030300', '전문직무 연수', 20),
    ('TSIX_030302', 3, 'TSIX_030300', '평생교육사 연수', 21),
    ('TSIX_030304', 3, 'TSIX_030300', '직무역량강화', 22),
    ('TSIX_040101', 3, 'TSIX_040100', '레저활동', 23),
    ('TSIX_040102', 3, 'TSIX_040100', '생활스포츠', 24),
    ('TSIX_040103', 3, 'TSIX_040100', '스포츠예술활동', 25),
    ('TSIX_040201', 3, 'TSIX_040200', '생활공예', 26),
    ('TSIX_040202', 3, 'TSIX_040200', '사진강좌', 27),
    ('TSIX_040203', 3, 'TSIX_040200', '노래교실', 28),
    ('TSIX_040301', 3, 'TSIX_040300', '미술', 29),
    ('TSIX_040302', 3, 'TSIX_040300', '서예', 30),
    ('TSIX_040303', 3, 'TSIX_040300', '예술공연 관람', 31),
    ('TSIX_050101', 3, 'TSIX_050100', '생활의료교육', 32),
    ('TSIX_050102', 3, 'TSIX_050100', '심리상담교육', 33),
    ('TSIX_050103', 3, 'TSIX_050100', '정신건강교육', 34),
    ('TSIX_050201', 3, 'TSIX_050200', '부모교육', 35),
    ('TSIX_050202', 3, 'TSIX_050200', '생활 외국어 및 컴퓨터', 36),
    ('TSIX_050203', 3, 'TSIX_050200', '가정생활', 37),
    ('TSIX_050204', 3, 'TSIX_050200', '요리', 38),
    ('TSIX_050301', 3, 'TSIX_050300', '역사교육', 39),
    ('TSIX_050302', 3, 'TSIX_050300', '교양교육', 40),
    ('TSIX_050303', 3, 'TSIX_050300', '인문학강좌', 41),
    ('TSIX_060101', 3, 'TSIX_060100', '주민자치교육', 42),
    ('TSIX_060102', 3, 'TSIX_060100', '환경생태교육', 43),
    ('TSIX_060103', 3, 'TSIX_060100', '지역관련 교육', 44),
    ('TSIX_060201', 3, 'TSIX_060200', '시민리더양성', 45),
    ('TSIX_060202', 3, 'TSIX_060200', '평생학습리더양성', 46),
    ('TSIX_060203', 3, 'TSIX_060200', '지역활동가양성', 47),
    ('TSIX_060301', 3, 'TSIX_060300', '학습동아리', 48),
    ('TSIX_060302', 3, 'TSIX_060300', '자원봉사활동', 49),
    ('TSIX_060401', 3, 'TSIX_060400', '행복한학교', 50),
    ('TSIX_060402', 3, 'TSIX_060400', '행복한대구생활', 51),
    ('TSIX_060403', 3, 'TSIX_060400', '행복대학', 52);

-- 강좌는 분류 코드 하나를 가진다. 초안에서는 비어도 되고 게시할 때 필수다(서비스가 검사).
ALTER TABLE tb_courses
    ADD COLUMN classification_code VARCHAR(11)
        CONSTRAINT fk_tb_courses_classification_code
        REFERENCES tb_course_classifications (code) ON DELETE RESTRICT;

CREATE INDEX ix_tb_courses_classification_code ON tb_courses (classification_code);
