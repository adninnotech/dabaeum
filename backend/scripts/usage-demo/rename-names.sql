-- 시연용: 가상 데이터(코드 SYN-)의 강좌·기관 이름을 운영처럼 보이는 이름으로 바꾸고, 가상 회원(@synthetic.invalid)의
-- 이름을 나이·성별에 맞는 한국 이름으로, 가입일을 시연 기준 오픈일(2026-08-17) 이후로 바꾼다 (docs/specs/usage-statistics.md §12).
-- dabaeum_dev 전용. 원래 이름은 demo_name_backup 에 한 번만 남긴다(다시 돌려도 처음 이름이 남는다). 되돌리기는 restore-names.sql.
-- 강좌 id·코드·분류·신청·수료는 건드리지 않는다. 가상 데이터 구분은 계속 코드 접두어 SYN- 로 한다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo names are for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

-- tb_ 접두어가 아니어서 Flyway 테이블 수 검사에 들지 않는다. 되돌리면 지운다.
CREATE TABLE IF NOT EXISTS demo_name_backup (
    kind     VARCHAR(20)  NOT NULL,
    id       UUID         NOT NULL,
    original VARCHAR(500) NOT NULL,
    PRIMARY KEY (kind, id)
);

INSERT INTO demo_name_backup (kind, id, original)
SELECT 'COURSE', id, title FROM tb_courses WHERE course_code LIKE 'SYN-%'
ON CONFLICT DO NOTHING;
INSERT INTO demo_name_backup (kind, id, original)
SELECT 'INSTITUTION', id, name FROM tb_institutions WHERE institution_code LIKE 'SYN-%'
ON CONFLICT DO NOTHING;
INSERT INTO demo_name_backup (kind, id, original)
SELECT 'USER_CREATED_AT', id, created_at::text FROM tb_users WHERE email LIKE '%@synthetic.invalid'
ON CONFLICT DO NOTHING;

-- 분류마다 제목 목록을 돌려 쓴다. 같은 분류에 목록보다 강좌가 많으면 뒤에 기수를 붙인다.
WITH pool (code, n, title) AS (VALUES
    ('TSIX_060301', 1, '독서토론 학습동아리'),
    ('TSIX_060301', 2, '마을기록 학습동아리'),
    ('TSIX_060301', 3, '시니어 사진 동아리'),
    ('TSIX_060301', 4, '그림책 읽기 모임'),
    ('TSIX_060301', 5, '우리동네 역사탐방 동아리'),
    ('TSIX_060301', 6, '생활영어 회화 동아리'),
    ('TSIX_060301', 7, '환경지킴이 학습동아리'),
    ('TSIX_060301', 8, '스마트폰 사진 동아리'),
    ('TSIX_060301', 9, '시 낭송 동아리'),
    ('TSIX_060301', 10, '통기타 연주 동아리'),
    ('TSIX_060301', 11, '캘리그라피 나눔 동아리'),
    ('TSIX_060301', 12, '북아트 학습동아리'),
    ('TSIX_060301', 13, '부모 공부모임'),
    ('TSIX_060301', 14, '걷기 건강 동아리'),
    ('TSIX_060301', 15, '시민 합창 동아리'),
    ('TSIX_030201', 1, '중장년 재취업 준비 과정'),
    ('TSIX_030201', 2, '이력서·자기소개서 작성 실습'),
    ('TSIX_030201', 3, '면접 이미지메이킹'),
    ('TSIX_030201', 4, '경력단절여성 취업 준비반'),
    ('TSIX_030201', 5, '신중년 인생설계와 재취업'),
    ('TSIX_030201', 6, '청년 취업 역량 강화'),
    ('TSIX_030201', 7, '직업상담 입문'),
    ('TSIX_030201', 8, '사회복지 현장 입문'),
    ('TSIX_030201', 9, '바리스타 취업 준비반'),
    ('TSIX_030201', 10, '요양보호사 취업 연계 과정'),
    ('TSIX_030201', 11, '온라인 면접 대비 실전'),
    ('TSIX_030201', 12, '취업을 위한 엑셀 기초'),
    ('TSIX_030201', 13, '창업과 취업 진로 설계'),
    ('TSIX_030201', 14, '공공기관 취업 준비 특강'),
    ('TSIX_030201', 15, '제2의 직업 찾기'),
    ('TSIX_050101', 1, '생활 속 응급처치'),
    ('TSIX_050101', 2, '심폐소생술 실습 교육'),
    ('TSIX_050101', 3, '시니어 건강관리 교실'),
    ('TSIX_050101', 4, '치매 예방 두뇌 건강'),
    ('TSIX_050101', 5, '약 바로 알기'),
    ('TSIX_050101', 6, '만성질환 관리와 식생활'),
    ('TSIX_050101', 7, '어르신 낙상 예방 운동'),
    ('TSIX_050101', 8, '건강한 수면 습관'),
    ('TSIX_050101', 9, '자연치유 건강 강좌'),
    ('TSIX_050101', 10, '마음건강 스트레스 관리'),
    ('TSIX_050101', 11, '우리 가족 건강 지킴이'),
    ('TSIX_050101', 12, '근골격계 통증 관리'),
    ('TSIX_050101', 13, '건강 걷기 교실'),
    ('TSIX_050101', 14, '아로마테라피 생활 건강'),
    ('TSIX_010201', 1, '왕초보 컴퓨터 기초'),
    ('TSIX_010201', 2, '어르신 컴퓨터 첫걸음'),
    ('TSIX_010201', 3, '엑셀 기초 따라하기'),
    ('TSIX_010201', 4, '한글 문서 작성 기초'),
    ('TSIX_010201', 5, '인터넷 활용과 정보검색'),
    ('TSIX_010201', 6, '컴퓨터 기초와 이메일 활용'),
    ('TSIX_010201', 7, '파워포인트 기초'),
    ('TSIX_010201', 8, '엑셀 실무 활용'),
    ('TSIX_010201', 9, '키오스크 사용법 익히기'),
    ('TSIX_010201', 10, '컴퓨터로 사진 정리하기'),
    ('TSIX_010201', 11, '디지털 금융 생활 기초'),
    ('TSIX_010201', 12, '온라인 민원 서비스 활용'),
    ('TSIX_010201', 13, '유튜브 시청과 계정 관리'),
    ('TSIX_030101', 1, '컴퓨터활용능력 2급 필기'),
    ('TSIX_030101', 2, 'ITQ 엑셀 자격증 과정'),
    ('TSIX_030101', 3, '한국사능력검정 대비반'),
    ('TSIX_030101', 4, '바리스타 2급 자격증 과정'),
    ('TSIX_030101', 5, '제과제빵기능사 실기'),
    ('TSIX_030101', 6, '한식조리기능사 준비반'),
    ('TSIX_030101', 7, '요양보호사 자격 준비'),
    ('TSIX_030101', 8, '공인중개사 기초 이론'),
    ('TSIX_030101', 9, '드론 자격증 이론 과정'),
    ('TSIX_030101', 10, '전산회계 2급'),
    ('TSIX_030101', 11, 'GTQ 포토샵 자격증'),
    ('TSIX_030101', 12, '심리상담사 자격 과정'),
    ('TSIX_030101', 13, '반려동물관리사 자격 과정'),
    ('TSIX_040203', 1, '신나는 노래교실'),
    ('TSIX_040203', 2, '트로트 노래교실'),
    ('TSIX_040203', 3, '추억의 7080 노래교실'),
    ('TSIX_040203', 4, '가곡 부르기'),
    ('TSIX_040203', 5, '시니어 합창 교실'),
    ('TSIX_040203', 6, '건강 노래교실'),
    ('TSIX_040203', 7, '발성과 호흡 기초'),
    ('TSIX_040203', 8, '동요 부르기 교실'),
    ('TSIX_040203', 9, '우쿨렐레와 함께 노래를'),
    ('TSIX_040203', 10, '통기타 노래교실'),
    ('TSIX_040203', 11, '팝송으로 배우는 영어 노래'),
    ('TSIX_060102', 1, '생활 속 탄소중립 실천'),
    ('TSIX_060102', 2, '제로웨이스트 생활 교실'),
    ('TSIX_060102', 3, '업사이클링 공예'),
    ('TSIX_060102', 4, '도시농업 텃밭 가꾸기'),
    ('TSIX_060102', 5, '숲해설과 생태 산책'),
    ('TSIX_060102', 6, '금호강 생태 탐방'),
    ('TSIX_060102', 7, '반려식물 키우기'),
    ('TSIX_060102', 8, '기후위기 시민 강좌'),
    ('TSIX_060102', 9, '친환경 세제 만들기'),
    ('TSIX_060102', 10, '새와 곤충 관찰 교실'),
    ('TSIX_060102', 11, '우리동네 환경지도 만들기'),
    ('TSIX_040201', 1, '가죽공예 소품 만들기'),
    ('TSIX_040201', 2, '도자기 물레 체험'),
    ('TSIX_040201', 3, '천연비누·캔들 만들기'),
    ('TSIX_040201', 4, '꽃꽂이와 플라워 디자인'),
    ('TSIX_040201', 5, '목공 DIY 기초'),
    ('TSIX_040201', 6, '프랑스 자수'),
    ('TSIX_040201', 7, '뜨개질 교실'),
    ('TSIX_040201', 8, '라탄 공예'),
    ('TSIX_040201', 9, '한지 공예'),
    ('TSIX_040201', 10, '레진아트 소품 만들기'),
    ('TSIX_050301', 1, '한국사 이야기'),
    ('TSIX_050301', 2, '대구 근대골목 역사 산책'),
    ('TSIX_050301', 3, '조선왕조실록으로 보는 역사'),
    ('TSIX_050301', 4, '세계사 교양 강좌'),
    ('TSIX_050301', 5, '근현대사 다시 읽기'),
    ('TSIX_050301', 6, '역사 속 인물 탐구'),
    ('TSIX_050301', 7, '문화유산 답사 교실'),
    ('TSIX_050301', 8, '삼국시대 이야기'),
    ('TSIX_050301', 9, '독립운동사와 대구'),
    ('TSIX_050301', 10, '한국사 기초 강좌'),
    ('TSIX_040301', 1, '수채화 기초'),
    ('TSIX_040301', 2, '어반스케치'),
    ('TSIX_040301', 3, '아크릴화 교실'),
    ('TSIX_040301', 4, '연필 드로잉 입문'),
    ('TSIX_040301', 5, '유화 그리기'),
    ('TSIX_040301', 6, '아이패드 드로잉'),
    ('TSIX_040301', 7, '민화 그리기'),
    ('TSIX_040301', 8, '색연필 일러스트'),
    ('TSIX_040301', 9, '인물 크로키'),
    ('TSIX_040301', 10, '그림으로 마음 읽기'),
    ('TSIX_040302', 1, '한글 서예 기초'),
    ('TSIX_040302', 2, '캘리그라피 입문'),
    ('TSIX_040302', 3, '붓글씨 교실'),
    ('TSIX_040302', 4, '생활 캘리그라피'),
    ('TSIX_040302', 5, '한문 서예'),
    ('TSIX_040302', 6, '문인화 기초'),
    ('TSIX_040302', 7, '캘리그라피 엽서 만들기'),
    ('TSIX_040302', 8, '서예와 마음 수양'),
    ('TSIX_040302', 9, '전각(도장) 새기기'),
    ('TSIX_040302', 10, '가훈 쓰기 교실'),
    ('TSIX_010202', 1, '어르신 스마트폰 첫걸음'),
    ('TSIX_010202', 2, '스마트폰 사진 잘 찍기'),
    ('TSIX_010202', 3, '스마트폰 영상편집'),
    ('TSIX_010202', 4, '카카오톡 200% 활용'),
    ('TSIX_010202', 5, '스마트폰 금융·결제 안전하게'),
    ('TSIX_010202', 6, '유튜브 영상 만들기'),
    ('TSIX_010202', 7, '스마트폰으로 블로그 시작'),
    ('TSIX_010202', 8, '모바일 민원·예약 활용'),
    ('TSIX_010202', 9, '스마트폰 보안과 사기 예방'),
    ('TSIX_010202', 10, 'AI 비서 활용하기'),
    ('TSIX_040102', 1, '아침 요가'),
    ('TSIX_040102', 2, '필라테스 기초'),
    ('TSIX_040102', 3, '생활 수영 초급'),
    ('TSIX_040102', 4, '탁구 교실'),
    ('TSIX_040102', 5, '줌바 댄스'),
    ('TSIX_040102', 6, '배드민턴 교실'),
    ('TSIX_040102', 7, '시니어 건강 체조'),
    ('TSIX_040102', 8, '골프 입문'),
    ('TSIX_040102', 9, '라인댄스'),
    ('TSIX_030304', 1, '파이썬 기초 프로그래밍'),
    ('TSIX_030304', 2, '업무에 바로 쓰는 엑셀'),
    ('TSIX_030304', 3, '생성형 AI 업무 활용'),
    ('TSIX_030304', 4, '데이터 분석 입문'),
    ('TSIX_030304', 5, '보고서 글쓰기'),
    ('TSIX_030304', 6, '프레젠테이션 스킬'),
    ('TSIX_030304', 7, '코딩으로 업무 자동화'),
    ('TSIX_030304', 8, '디지털 마케팅 기초'),
    ('TSIX_050303', 1, '철학이 있는 저녁'),
    ('TSIX_050303', 2, '인문학으로 읽는 영화'),
    ('TSIX_050303', 3, '고전 읽기 모임'),
    ('TSIX_050303', 4, '심리학 교양 강좌'),
    ('TSIX_050303', 5, '글쓰기와 자서전 쓰기'),
    ('TSIX_050303', 6, '시 읽는 시간'),
    ('TSIX_050303', 7, '인문학 산책'),
    ('TSIX_050303', 8, '독서 토론 교실'),
    ('TSIX_050204', 1, '집밥 반찬 만들기'),
    ('TSIX_050204', 2, '남성 요리 교실'),
    ('TSIX_050204', 3, '건강 저염 요리'),
    ('TSIX_050204', 4, '베이킹 기초'),
    ('TSIX_050204', 5, '제철 한식 요리'),
    ('TSIX_050204', 6, '브런치 카페 요리'),
    ('TSIX_050204', 7, '아이와 함께하는 요리'),
    ('TSIX_030103', 1, '토익 기초 600'),
    ('TSIX_030103', 2, '생활 영어 회화'),
    ('TSIX_030103', 3, '일본어 JLPT N4'),
    ('TSIX_030103', 4, '중국어 HSK 3급'),
    ('TSIX_030103', 5, 'MOS 엑셀 자격 과정'),
    ('TSIX_030103', 6, '컴퓨터활용능력 실기'),
    ('TSIX_030103', 7, '영어 회화 중급'),
    ('TSIX_040103', 1, '댄스스포츠 기초'),
    ('TSIX_040103', 2, '한국무용'),
    ('TSIX_040103', 3, 'K-POP 댄스'),
    ('TSIX_040103', 4, '발레 핏'),
    ('TSIX_040103', 5, '태극권'),
    ('TSIX_040103', 6, '스포츠 클라이밍 입문'),
    ('TSIX_040103', 7, '탭댄스'),
    ('TSIX_040101', 1, '등산 안전 교실'),
    ('TSIX_040101', 2, '사진 출사 여행'),
    ('TSIX_040101', 3, '캠핑 기초'),
    ('TSIX_040101', 4, '자전거 여행 교실'),
    ('TSIX_040101', 5, '낚시 입문'),
    ('TSIX_040101', 6, '트레킹 코스 탐방'),
    ('TSIX_050302', 1, '생활 법률 상식'),
    ('TSIX_050302', 2, '재테크와 노후 설계'),
    ('TSIX_050302', 3, '부동산 기초 상식'),
    ('TSIX_050302', 4, '와인과 교양'),
    ('TSIX_050302', 5, '커피 교양 강좌'),
    ('TSIX_050302', 6, '반려동물과 함께 살기')
), sized AS (
    SELECT code, n, title, COUNT(*) OVER (PARTITION BY code) AS size FROM pool
), ranked AS (
    SELECT c.id, c.classification_code AS code,
           row_number() OVER (PARTITION BY c.classification_code ORDER BY c.course_code) AS rn
      FROM tb_courses c WHERE c.course_code LIKE 'SYN-%'
)
UPDATE tb_courses c
   SET title = s.title || CASE WHEN (r.rn - 1) / s.size > 0 THEN ' ' || ((r.rn - 1) / s.size + 1) || '기' ELSE '' END
  FROM ranked r JOIN sized s ON s.code = r.code AND s.n = (r.rn - 1) % s.size + 1
 WHERE c.id = r.id;

WITH pool (n, name) AS (VALUES
    (1, '앞산 평생교육원'),
    (2, '금호강 시민학교'),
    (3, '팔공 평생학습센터'),
    (4, '신천 배움터'),
    (5, '달구벌 시민대학'),
    (6, '동촌 문화교육원'),
    (7, '와룡 평생학습원'),
    (8, '두류 시민교육센터'),
    (9, '비슬 배움마당'),
    (10, '수성못 문화학교'),
    (11, '칠곡 평생교육원'),
    (12, '서문 시민배움터')
), ranked AS (
    SELECT id, row_number() OVER (ORDER BY institution_code) AS rn
      FROM tb_institutions WHERE institution_code LIKE 'SYN-%'
)
UPDATE tb_institutions i
   SET name = p.name || CASE WHEN r.rn > 12 THEN ' ' || r.rn ELSE '' END
  FROM ranked r JOIN pool p ON p.n = (r.rn - 1) % 12 + 1
 WHERE i.id = r.id;

-- 가입일: 오픈 직후 몰리고(첫날 무게 4, 열흘마다 약 1/e 로 줄어) 평소 수준(무게 1)으로 이어진다. 주말 60%, 날마다 ±20%.
-- 08:00~22:00 사이 아무 시각. 다시 돌리면 새로 나눈다(원래 가입일은 백업에 처음 값이 남는다).
WITH days AS (
    SELECT g.d::date AS d,
           (3.0 * exp(-(g.d::date - DATE '2026-08-17') / 10.0) + 1)
               * CASE WHEN EXTRACT(ISODOW FROM g.d) IN (6, 7) THEN 0.6 ELSE 1 END
               * (0.8 + random() * 0.4) AS w
      FROM generate_series(DATE '2026-08-17', DATE '2026-10-29', INTERVAL '1 day') AS g(d)
), cum AS (
    SELECT d, SUM(w) OVER (ORDER BY d) - w AS lo, SUM(w) OVER (ORDER BY d) AS hi, SUM(w) OVER () AS total FROM days
), picked AS (
    SELECT id, random() AS r FROM tb_users WHERE email LIKE '%@synthetic.invalid'
)
UPDATE tb_users u
   SET created_at = (c.d + (8 + random() * 14) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Seoul'
  FROM picked p JOIN cum c ON p.r * c.total >= c.lo AND p.r * c.total < c.hi
 WHERE u.id = p.id;

-- 회원 나이·성별 (§12 결정 13). 가상 회원은 20~79세·남녀 반반으로 고르게 만들어져 통계 막대가 평평하다.
-- 서울시 평생학습포털 2015 실제 회원 분포(40대 29%·50대 21%, 여성 52.5%)를 대구 2026 에 맞게 5~10세 올린 기준으로 다시 나눈다:
-- 10대 1%, 20대 9%, 30대 17%, 40대 26%, 50대 26%, 60대 15%, 70대 5%, 미상 1% / 여성 56%, 남성 40%, 밝히지 않음 2%, 미상 2%.
-- QA 데모 계정(syn-00617·01091·01775)은 문서에 나이가 적혀 있어 그대로 둔다. 원래 값은 백업에 남긴다.
INSERT INTO demo_name_backup (kind, id, original)
SELECT 'USER_BIRTH_DATE', id, COALESCE(birth_date::text, '') FROM tb_users WHERE email LIKE '%@synthetic.invalid'
ON CONFLICT DO NOTHING;
INSERT INTO demo_name_backup (kind, id, original)
SELECT 'USER_GENDER', id, COALESCE(gender, '') FROM tb_users WHERE email LIKE '%@synthetic.invalid'
ON CONFLICT DO NOTHING;

WITH picked AS (
    SELECT id, random() AS ra, random() AS rg, random() AS ry, random() AS rd
      FROM tb_users
     WHERE email LIKE '%@synthetic.invalid'
       AND email NOT IN ('syn-00617@synthetic.invalid', 'syn-01091@synthetic.invalid', 'syn-01775@synthetic.invalid')
), banded AS (
    SELECT id, rg, rd,
           CASE WHEN ra < 0.01 THEN NULL
                WHEN ra < 0.02 THEN 17 + floor(ry * 3)
                WHEN ra < 0.11 THEN 20 + floor(ry * 10)
                WHEN ra < 0.28 THEN 30 + floor(ry * 10)
                WHEN ra < 0.54 THEN 40 + floor(ry * 10)
                WHEN ra < 0.80 THEN 50 + floor(ry * 10)
                WHEN ra < 0.95 THEN 60 + floor(ry * 10)
                ELSE 70 + floor(ry * 10) END AS age
      FROM picked
)
UPDATE tb_users u
   SET birth_date = CASE WHEN b.age IS NULL THEN NULL
                         ELSE (now() AT TIME ZONE 'Asia/Seoul')::date - (b.age * 365.25 + floor(b.rd * 365))::int END,
       gender = CASE WHEN b.rg < 0.56 THEN 'FEMALE' WHEN b.rg < 0.96 THEN 'MALE'
                     WHEN b.rg < 0.98 THEN 'UNDISCLOSED' ELSE NULL END
  FROM banded b
 WHERE u.id = b.id;

-- 회원 이름 (§12 결정 9). 성은 실제 성씨 비율대로(드문 성·복성 남궁·황보 등 포함), 이름은 대부분 출생 연대(1940~2000년대)·성별마다
-- 그 시절 흔한 이름 30개 중 하나(세계일보 2016-05-09 대법원 출생신고 분석과 같은 흐름). 섞는 비율: 외자(이름 한 글자) 3%,
-- 1980년대생 이후 순우리말 이름 4%·세 글자 이름 1%. 그래서 2~5글자 이름이 섞인다. 성별이 없으면 남녀 중 무작위.
-- 가상 회원은 수료증이 없어 이름 사본이 다른 테이블에 없다.
INSERT INTO demo_name_backup (kind, id, original)
SELECT 'USER_NAME', id, name FROM tb_users WHERE email LIKE '%@synthetic.invalid'
ON CONFLICT DO NOTHING;

WITH given (kind, gender, decade, n, name) AS (VALUES
    ('NORMAL', 'MALE', 1940, 1, '영수'), ('NORMAL', 'MALE', 1940, 2, '영호'), ('NORMAL', 'MALE', 1940, 3, '영식'), ('NORMAL', 'MALE', 1940, 4, '영길'),
    ('NORMAL', 'MALE', 1940, 5, '정수'), ('NORMAL', 'MALE', 1940, 6, '상철'), ('NORMAL', 'MALE', 1940, 7, '종수'), ('NORMAL', 'MALE', 1940, 8, '광수'),
    ('NORMAL', 'MALE', 1940, 9, '성수'), ('NORMAL', 'MALE', 1940, 10, '병철'), ('NORMAL', 'MALE', 1940, 11, '재호'), ('NORMAL', 'MALE', 1940, 12, '춘식'),
    ('NORMAL', 'MALE', 1940, 13, '만수'), ('NORMAL', 'MALE', 1940, 14, '용수'), ('NORMAL', 'MALE', 1940, 15, '기철'), ('NORMAL', 'MALE', 1940, 16, '수남'),
    ('NORMAL', 'MALE', 1940, 17, '판석'), ('NORMAL', 'MALE', 1940, 18, '덕수'), ('NORMAL', 'MALE', 1940, 19, '봉수'), ('NORMAL', 'MALE', 1940, 20, '길수'),
    ('NORMAL', 'MALE', 1940, 21, '창수'), ('NORMAL', 'MALE', 1940, 22, '경수'), ('NORMAL', 'MALE', 1940, 23, '순길'), ('NORMAL', 'MALE', 1940, 24, '상호'),
    ('NORMAL', 'MALE', 1940, 25, '정남'), ('NORMAL', 'MALE', 1940, 26, '태식'), ('NORMAL', 'MALE', 1940, 27, '일남'), ('NORMAL', 'MALE', 1940, 28, '동식'),
    ('NORMAL', 'MALE', 1940, 29, '갑수'), ('NORMAL', 'MALE', 1940, 30, '용호'), ('NORMAL', 'MALE', 1950, 1, '영수'), ('NORMAL', 'MALE', 1950, 2, '정호'),
    ('NORMAL', 'MALE', 1950, 3, '상철'), ('NORMAL', 'MALE', 1950, 4, '영식'), ('NORMAL', 'MALE', 1950, 5, '종호'), ('NORMAL', 'MALE', 1950, 6, '명수'),
    ('NORMAL', 'MALE', 1950, 7, '성호'), ('NORMAL', 'MALE', 1950, 8, '기수'), ('NORMAL', 'MALE', 1950, 9, '영환'), ('NORMAL', 'MALE', 1950, 10, '진호'),
    ('NORMAL', 'MALE', 1950, 11, '재영'), ('NORMAL', 'MALE', 1950, 12, '병수'), ('NORMAL', 'MALE', 1950, 13, '동철'), ('NORMAL', 'MALE', 1950, 14, '광호'),
    ('NORMAL', 'MALE', 1950, 15, '영일'), ('NORMAL', 'MALE', 1950, 16, '영철'), ('NORMAL', 'MALE', 1950, 17, '정식'), ('NORMAL', 'MALE', 1950, 18, '대식'),
    ('NORMAL', 'MALE', 1950, 19, '상만'), ('NORMAL', 'MALE', 1950, 20, '성태'), ('NORMAL', 'MALE', 1950, 21, '재근'), ('NORMAL', 'MALE', 1950, 22, '용철'),
    ('NORMAL', 'MALE', 1950, 23, '종철'), ('NORMAL', 'MALE', 1950, 24, '상균'), ('NORMAL', 'MALE', 1950, 25, '희철'), ('NORMAL', 'MALE', 1950, 26, '정길'),
    ('NORMAL', 'MALE', 1950, 27, '경호'), ('NORMAL', 'MALE', 1950, 28, '명호'), ('NORMAL', 'MALE', 1950, 29, '석진'), ('NORMAL', 'MALE', 1950, 30, '철수'),
    ('NORMAL', 'MALE', 1960, 1, '성호'), ('NORMAL', 'MALE', 1960, 2, '영진'), ('NORMAL', 'MALE', 1960, 3, '정훈'), ('NORMAL', 'MALE', 1960, 4, '상훈'),
    ('NORMAL', 'MALE', 1960, 5, '성진'), ('NORMAL', 'MALE', 1960, 6, '재영'), ('NORMAL', 'MALE', 1960, 7, '동수'), ('NORMAL', 'MALE', 1960, 8, '종훈'),
    ('NORMAL', 'MALE', 1960, 9, '경수'), ('NORMAL', 'MALE', 1960, 10, '정호'), ('NORMAL', 'MALE', 1960, 11, '승철'), ('NORMAL', 'MALE', 1960, 12, '재성'),
    ('NORMAL', 'MALE', 1960, 13, '명수'), ('NORMAL', 'MALE', 1960, 14, '성민'), ('NORMAL', 'MALE', 1960, 15, '진수'), ('NORMAL', 'MALE', 1960, 16, '영철'),
    ('NORMAL', 'MALE', 1960, 17, '영호'), ('NORMAL', 'MALE', 1960, 18, '상욱'), ('NORMAL', 'MALE', 1960, 19, '병훈'), ('NORMAL', 'MALE', 1960, 20, '태호'),
    ('NORMAL', 'MALE', 1960, 21, '기현'), ('NORMAL', 'MALE', 1960, 22, '성규'), ('NORMAL', 'MALE', 1960, 23, '재석'), ('NORMAL', 'MALE', 1960, 24, '정우'),
    ('NORMAL', 'MALE', 1960, 25, '현철'), ('NORMAL', 'MALE', 1960, 26, '동욱'), ('NORMAL', 'MALE', 1960, 27, '상진'), ('NORMAL', 'MALE', 1960, 28, '종민'),
    ('NORMAL', 'MALE', 1960, 29, '인철'), ('NORMAL', 'MALE', 1960, 30, '윤석'), ('NORMAL', 'MALE', 1970, 1, '성훈'), ('NORMAL', 'MALE', 1970, 2, '정훈'),
    ('NORMAL', 'MALE', 1970, 3, '상현'), ('NORMAL', 'MALE', 1970, 4, '준호'), ('NORMAL', 'MALE', 1970, 5, '재훈'), ('NORMAL', 'MALE', 1970, 6, '동훈'),
    ('NORMAL', 'MALE', 1970, 7, '영민'), ('NORMAL', 'MALE', 1970, 8, '성철'), ('NORMAL', 'MALE', 1970, 9, '지훈'), ('NORMAL', 'MALE', 1970, 10, '승현'),
    ('NORMAL', 'MALE', 1970, 11, '현석'), ('NORMAL', 'MALE', 1970, 12, '민수'), ('NORMAL', 'MALE', 1970, 13, '정민'), ('NORMAL', 'MALE', 1970, 14, '경민'),
    ('NORMAL', 'MALE', 1970, 15, '상우'), ('NORMAL', 'MALE', 1970, 16, '성호'), ('NORMAL', 'MALE', 1970, 17, '동현'), ('NORMAL', 'MALE', 1970, 18, '재민'),
    ('NORMAL', 'MALE', 1970, 19, '진우'), ('NORMAL', 'MALE', 1970, 20, '태훈'), ('NORMAL', 'MALE', 1970, 21, '준석'), ('NORMAL', 'MALE', 1970, 22, '형준'),
    ('NORMAL', 'MALE', 1970, 23, '상민'), ('NORMAL', 'MALE', 1970, 24, '대현'), ('NORMAL', 'MALE', 1970, 25, '종현'), ('NORMAL', 'MALE', 1970, 26, '윤호'),
    ('NORMAL', 'MALE', 1970, 27, '기훈'), ('NORMAL', 'MALE', 1970, 28, '성우'), ('NORMAL', 'MALE', 1970, 29, '현수'), ('NORMAL', 'MALE', 1970, 30, '재웅'),
    ('NORMAL', 'MALE', 1980, 1, '지훈'), ('NORMAL', 'MALE', 1980, 2, '성민'), ('NORMAL', 'MALE', 1980, 3, '현우'), ('NORMAL', 'MALE', 1980, 4, '준영'),
    ('NORMAL', 'MALE', 1980, 5, '동현'), ('NORMAL', 'MALE', 1980, 6, '민석'), ('NORMAL', 'MALE', 1980, 7, '상민'), ('NORMAL', 'MALE', 1980, 8, '정현'),
    ('NORMAL', 'MALE', 1980, 9, '재현'), ('NORMAL', 'MALE', 1980, 10, '준혁'), ('NORMAL', 'MALE', 1980, 11, '승민'), ('NORMAL', 'MALE', 1980, 12, '우진'),
    ('NORMAL', 'MALE', 1980, 13, '민호'), ('NORMAL', 'MALE', 1980, 14, '현준'), ('NORMAL', 'MALE', 1980, 15, '태현'), ('NORMAL', 'MALE', 1980, 16, '요한'),
    ('NORMAL', 'MALE', 1980, 17, '지환'), ('NORMAL', 'MALE', 1980, 18, '상훈'), ('NORMAL', 'MALE', 1980, 19, '경태'), ('NORMAL', 'MALE', 1980, 20, '민규'),
    ('NORMAL', 'MALE', 1980, 21, '성재'), ('NORMAL', 'MALE', 1980, 22, '진혁'), ('NORMAL', 'MALE', 1980, 23, '동욱'), ('NORMAL', 'MALE', 1980, 24, '영훈'),
    ('NORMAL', 'MALE', 1980, 25, '재석'), ('NORMAL', 'MALE', 1980, 26, '도형'), ('NORMAL', 'MALE', 1980, 27, '현석'), ('NORMAL', 'MALE', 1980, 28, '승우'),
    ('NORMAL', 'MALE', 1980, 29, '정우'), ('NORMAL', 'MALE', 1980, 30, '대웅'), ('NORMAL', 'MALE', 1990, 1, '민준'), ('NORMAL', 'MALE', 1990, 2, '현우'),
    ('NORMAL', 'MALE', 1990, 3, '지훈'), ('NORMAL', 'MALE', 1990, 4, '동현'), ('NORMAL', 'MALE', 1990, 5, '준호'), ('NORMAL', 'MALE', 1990, 6, '성민'),
    ('NORMAL', 'MALE', 1990, 7, '승현'), ('NORMAL', 'MALE', 1990, 8, '민재'), ('NORMAL', 'MALE', 1990, 9, '도현'), ('NORMAL', 'MALE', 1990, 10, '재원'),
    ('NORMAL', 'MALE', 1990, 11, '진우'), ('NORMAL', 'MALE', 1990, 12, '건우'), ('NORMAL', 'MALE', 1990, 13, '준서'), ('NORMAL', 'MALE', 1990, 14, '태윤'),
    ('NORMAL', 'MALE', 1990, 15, '시우'), ('NORMAL', 'MALE', 1990, 16, '민혁'), ('NORMAL', 'MALE', 1990, 17, '준혁'), ('NORMAL', 'MALE', 1990, 18, '승우'),
    ('NORMAL', 'MALE', 1990, 19, '성현'), ('NORMAL', 'MALE', 1990, 20, '지환'), ('NORMAL', 'MALE', 1990, 21, '상우'), ('NORMAL', 'MALE', 1990, 22, '영재'),
    ('NORMAL', 'MALE', 1990, 23, '준기'), ('NORMAL', 'MALE', 1990, 24, '태민'), ('NORMAL', 'MALE', 1990, 25, '현수'), ('NORMAL', 'MALE', 1990, 26, '재혁'),
    ('NORMAL', 'MALE', 1990, 27, '민성'), ('NORMAL', 'MALE', 1990, 28, '우현'), ('NORMAL', 'MALE', 1990, 29, '정민'), ('NORMAL', 'MALE', 1990, 30, '동하'),
    ('NORMAL', 'MALE', 2000, 1, '민준'), ('NORMAL', 'MALE', 2000, 2, '서준'), ('NORMAL', 'MALE', 2000, 3, '도윤'), ('NORMAL', 'MALE', 2000, 4, '예준'),
    ('NORMAL', 'MALE', 2000, 5, '시우'), ('NORMAL', 'MALE', 2000, 6, '하준'), ('NORMAL', 'MALE', 2000, 7, '주원'), ('NORMAL', 'MALE', 2000, 8, '지호'),
    ('NORMAL', 'MALE', 2000, 9, '지후'), ('NORMAL', 'MALE', 2000, 10, '준우'), ('NORMAL', 'MALE', 2000, 11, '건우'), ('NORMAL', 'MALE', 2000, 12, '현우'),
    ('NORMAL', 'MALE', 2000, 13, '우진'), ('NORMAL', 'MALE', 2000, 14, '선우'), ('NORMAL', 'MALE', 2000, 15, '유준'), ('NORMAL', 'MALE', 2000, 16, '은우'),
    ('NORMAL', 'MALE', 2000, 17, '정우'), ('NORMAL', 'MALE', 2000, 18, '민재'), ('NORMAL', 'MALE', 2000, 19, '연우'), ('NORMAL', 'MALE', 2000, 20, '지훈'),
    ('NORMAL', 'MALE', 2000, 21, '승민'), ('NORMAL', 'MALE', 2000, 22, '시윤'), ('NORMAL', 'MALE', 2000, 23, '준혁'), ('NORMAL', 'MALE', 2000, 24, '이준'),
    ('NORMAL', 'MALE', 2000, 25, '윤호'), ('NORMAL', 'MALE', 2000, 26, '태윤'), ('NORMAL', 'MALE', 2000, 27, '도현'), ('NORMAL', 'MALE', 2000, 28, '서진'),
    ('NORMAL', 'MALE', 2000, 29, '하율'), ('NORMAL', 'MALE', 2000, 30, '지안'), ('NORMAL', 'FEMALE', 1940, 1, '영자'), ('NORMAL', 'FEMALE', 1940, 2, '순자'),
    ('NORMAL', 'FEMALE', 1940, 3, '정자'), ('NORMAL', 'FEMALE', 1940, 4, '영숙'), ('NORMAL', 'FEMALE', 1940, 5, '정숙'), ('NORMAL', 'FEMALE', 1940, 6, '명숙'),
    ('NORMAL', 'FEMALE', 1940, 7, '경숙'), ('NORMAL', 'FEMALE', 1940, 8, '영희'), ('NORMAL', 'FEMALE', 1940, 9, '순희'), ('NORMAL', 'FEMALE', 1940, 10, '옥자'),
    ('NORMAL', 'FEMALE', 1940, 11, '춘자'), ('NORMAL', 'FEMALE', 1940, 12, '말순'), ('NORMAL', 'FEMALE', 1940, 13, '정순'), ('NORMAL', 'FEMALE', 1940, 14, '영순'),
    ('NORMAL', 'FEMALE', 1940, 15, '미자'), ('NORMAL', 'FEMALE', 1940, 16, '복순'), ('NORMAL', 'FEMALE', 1940, 17, '순례'), ('NORMAL', 'FEMALE', 1940, 18, '귀남'),
    ('NORMAL', 'FEMALE', 1940, 19, '금순'), ('NORMAL', 'FEMALE', 1940, 20, '정애'), ('NORMAL', 'FEMALE', 1940, 21, '봉순'), ('NORMAL', 'FEMALE', 1940, 22, '순이'),
    ('NORMAL', 'FEMALE', 1940, 23, '분이'), ('NORMAL', 'FEMALE', 1940, 24, '옥순'), ('NORMAL', 'FEMALE', 1940, 25, '금자'), ('NORMAL', 'FEMALE', 1940, 26, '춘희'),
    ('NORMAL', 'FEMALE', 1940, 27, '연희'), ('NORMAL', 'FEMALE', 1940, 28, '길자'), ('NORMAL', 'FEMALE', 1940, 29, '점순'), ('NORMAL', 'FEMALE', 1940, 30, '필순'),
    ('NORMAL', 'FEMALE', 1950, 1, '영숙'), ('NORMAL', 'FEMALE', 1950, 2, '정숙'), ('NORMAL', 'FEMALE', 1950, 3, '명자'), ('NORMAL', 'FEMALE', 1950, 4, '순옥'),
    ('NORMAL', 'FEMALE', 1950, 5, '경자'), ('NORMAL', 'FEMALE', 1950, 6, '영옥'), ('NORMAL', 'FEMALE', 1950, 7, '정희'), ('NORMAL', 'FEMALE', 1950, 8, '영미'),
    ('NORMAL', 'FEMALE', 1950, 9, '숙자'), ('NORMAL', 'FEMALE', 1950, 10, '옥순'), ('NORMAL', 'FEMALE', 1950, 11, '명희'), ('NORMAL', 'FEMALE', 1950, 12, '경애'),
    ('NORMAL', 'FEMALE', 1950, 13, '혜숙'), ('NORMAL', 'FEMALE', 1950, 14, '순덕'), ('NORMAL', 'FEMALE', 1950, 15, '정례'), ('NORMAL', 'FEMALE', 1950, 16, '영애'),
    ('NORMAL', 'FEMALE', 1950, 17, '경순'), ('NORMAL', 'FEMALE', 1950, 18, '순남'), ('NORMAL', 'FEMALE', 1950, 19, '정옥'), ('NORMAL', 'FEMALE', 1950, 20, '영란'),
    ('NORMAL', 'FEMALE', 1950, 21, '화자'), ('NORMAL', 'FEMALE', 1950, 22, '희숙'), ('NORMAL', 'FEMALE', 1950, 23, '숙희'), ('NORMAL', 'FEMALE', 1950, 24, '명순'),
    ('NORMAL', 'FEMALE', 1950, 25, '춘화'), ('NORMAL', 'FEMALE', 1950, 26, '영심'), ('NORMAL', 'FEMALE', 1950, 27, '옥희'), ('NORMAL', 'FEMALE', 1950, 28, '정임'),
    ('NORMAL', 'FEMALE', 1950, 29, '미옥'), ('NORMAL', 'FEMALE', 1950, 30, '순임'), ('NORMAL', 'FEMALE', 1960, 1, '미경'), ('NORMAL', 'FEMALE', 1960, 2, '미숙'),
    ('NORMAL', 'FEMALE', 1960, 3, '경희'), ('NORMAL', 'FEMALE', 1960, 4, '은희'), ('NORMAL', 'FEMALE', 1960, 5, '미영'), ('NORMAL', 'FEMALE', 1960, 6, '정희'),
    ('NORMAL', 'FEMALE', 1960, 7, '영미'), ('NORMAL', 'FEMALE', 1960, 8, '현숙'), ('NORMAL', 'FEMALE', 1960, 9, '경숙'), ('NORMAL', 'FEMALE', 1960, 10, '혜숙'),
    ('NORMAL', 'FEMALE', 1960, 11, '순영'), ('NORMAL', 'FEMALE', 1960, 12, '정미'), ('NORMAL', 'FEMALE', 1960, 13, '은숙'), ('NORMAL', 'FEMALE', 1960, 14, '명희'),
    ('NORMAL', 'FEMALE', 1960, 15, '선희'), ('NORMAL', 'FEMALE', 1960, 16, '영숙'), ('NORMAL', 'FEMALE', 1960, 17, '순자'), ('NORMAL', 'FEMALE', 1960, 18, '경애'),
    ('NORMAL', 'FEMALE', 1960, 19, '미란'), ('NORMAL', 'FEMALE', 1960, 20, '인숙'), ('NORMAL', 'FEMALE', 1960, 21, '혜경'), ('NORMAL', 'FEMALE', 1960, 22, '연숙'),
    ('NORMAL', 'FEMALE', 1960, 23, '정란'), ('NORMAL', 'FEMALE', 1960, 24, '숙영'), ('NORMAL', 'FEMALE', 1960, 25, '경미'), ('NORMAL', 'FEMALE', 1960, 26, '성희'),
    ('NORMAL', 'FEMALE', 1960, 27, '윤희'), ('NORMAL', 'FEMALE', 1960, 28, '은미'), ('NORMAL', 'FEMALE', 1960, 29, '미향'), ('NORMAL', 'FEMALE', 1960, 30, '명옥'),
    ('NORMAL', 'FEMALE', 1970, 1, '은정'), ('NORMAL', 'FEMALE', 1970, 2, '미영'), ('NORMAL', 'FEMALE', 1970, 3, '현정'), ('NORMAL', 'FEMALE', 1970, 4, '지영'),
    ('NORMAL', 'FEMALE', 1970, 5, '은주'), ('NORMAL', 'FEMALE', 1970, 6, '미정'), ('NORMAL', 'FEMALE', 1970, 7, '수진'), ('NORMAL', 'FEMALE', 1970, 8, '정은'),
    ('NORMAL', 'FEMALE', 1970, 9, '은영'), ('NORMAL', 'FEMALE', 1970, 10, '혜진'), ('NORMAL', 'FEMALE', 1970, 11, '선영'), ('NORMAL', 'FEMALE', 1970, 12, '미선'),
    ('NORMAL', 'FEMALE', 1970, 13, '지현'), ('NORMAL', 'FEMALE', 1970, 14, '경아'), ('NORMAL', 'FEMALE', 1970, 15, '윤정'), ('NORMAL', 'FEMALE', 1970, 16, '은경'),
    ('NORMAL', 'FEMALE', 1970, 17, '미라'), ('NORMAL', 'FEMALE', 1970, 18, '희정'), ('NORMAL', 'FEMALE', 1970, 19, '수경'), ('NORMAL', 'FEMALE', 1970, 20, '현주'),
    ('NORMAL', 'FEMALE', 1970, 21, '지은'), ('NORMAL', 'FEMALE', 1970, 22, '소영'), ('NORMAL', 'FEMALE', 1970, 23, '미진'), ('NORMAL', 'FEMALE', 1970, 24, '혜영'),
    ('NORMAL', 'FEMALE', 1970, 25, '선미'), ('NORMAL', 'FEMALE', 1970, 26, '정아'), ('NORMAL', 'FEMALE', 1970, 27, '은미'), ('NORMAL', 'FEMALE', 1970, 28, '유경'),
    ('NORMAL', 'FEMALE', 1970, 29, '주희'), ('NORMAL', 'FEMALE', 1970, 30, '진희'), ('NORMAL', 'FEMALE', 1980, 1, '지은'), ('NORMAL', 'FEMALE', 1980, 2, '지혜'),
    ('NORMAL', 'FEMALE', 1980, 3, '수진'), ('NORMAL', 'FEMALE', 1980, 4, '민정'), ('NORMAL', 'FEMALE', 1980, 5, '은지'), ('NORMAL', 'FEMALE', 1980, 6, '지현'),
    ('NORMAL', 'FEMALE', 1980, 7, '현주'), ('NORMAL', 'FEMALE', 1980, 8, '혜진'), ('NORMAL', 'FEMALE', 1980, 9, '유진'), ('NORMAL', 'FEMALE', 1980, 10, '선영'),
    ('NORMAL', 'FEMALE', 1980, 11, '수정'), ('NORMAL', 'FEMALE', 1980, 12, '미진'), ('NORMAL', 'FEMALE', 1980, 13, '지연'), ('NORMAL', 'FEMALE', 1980, 14, '은영'),
    ('NORMAL', 'FEMALE', 1980, 15, '보람'), ('NORMAL', 'FEMALE', 1980, 16, '아름'), ('NORMAL', 'FEMALE', 1980, 17, '지영'), ('NORMAL', 'FEMALE', 1980, 18, '소영'),
    ('NORMAL', 'FEMALE', 1980, 19, '미영'), ('NORMAL', 'FEMALE', 1980, 20, '혜원'), ('NORMAL', 'FEMALE', 1980, 21, '은혜'), ('NORMAL', 'FEMALE', 1980, 22, '정민'),
    ('NORMAL', 'FEMALE', 1980, 23, '수연'), ('NORMAL', 'FEMALE', 1980, 24, '하나'), ('NORMAL', 'FEMALE', 1980, 25, '미란'), ('NORMAL', 'FEMALE', 1980, 26, '나영'),
    ('NORMAL', 'FEMALE', 1980, 27, '윤주'), ('NORMAL', 'FEMALE', 1980, 28, '지원'), ('NORMAL', 'FEMALE', 1980, 29, '다혜'), ('NORMAL', 'FEMALE', 1980, 30, '예슬'),
    ('NORMAL', 'FEMALE', 1990, 1, '지은'), ('NORMAL', 'FEMALE', 1990, 2, '수빈'), ('NORMAL', 'FEMALE', 1990, 3, '민지'), ('NORMAL', 'FEMALE', 1990, 4, '지현'),
    ('NORMAL', 'FEMALE', 1990, 5, '예진'), ('NORMAL', 'FEMALE', 1990, 6, '유진'), ('NORMAL', 'FEMALE', 1990, 7, '서현'), ('NORMAL', 'FEMALE', 1990, 8, '수연'),
    ('NORMAL', 'FEMALE', 1990, 9, '지원'), ('NORMAL', 'FEMALE', 1990, 10, '혜원'), ('NORMAL', 'FEMALE', 1990, 11, '다은'), ('NORMAL', 'FEMALE', 1990, 12, '은비'),
    ('NORMAL', 'FEMALE', 1990, 13, '가영'), ('NORMAL', 'FEMALE', 1990, 14, '소연'), ('NORMAL', 'FEMALE', 1990, 15, '하은'), ('NORMAL', 'FEMALE', 1990, 16, '지혜'),
    ('NORMAL', 'FEMALE', 1990, 17, '유나'), ('NORMAL', 'FEMALE', 1990, 18, '민경'), ('NORMAL', 'FEMALE', 1990, 19, '수진'), ('NORMAL', 'FEMALE', 1990, 20, '예지'),
    ('NORMAL', 'FEMALE', 1990, 21, '채원'), ('NORMAL', 'FEMALE', 1990, 22, '세영'), ('NORMAL', 'FEMALE', 1990, 23, '보미'), ('NORMAL', 'FEMALE', 1990, 24, '혜린'),
    ('NORMAL', 'FEMALE', 1990, 25, '지수'), ('NORMAL', 'FEMALE', 1990, 26, '나연'), ('NORMAL', 'FEMALE', 1990, 27, '은서'), ('NORMAL', 'FEMALE', 1990, 28, '현지'),
    ('NORMAL', 'FEMALE', 1990, 29, '소희'), ('NORMAL', 'FEMALE', 1990, 30, '아영'), ('NORMAL', 'FEMALE', 2000, 1, '서연'), ('NORMAL', 'FEMALE', 2000, 2, '서윤'),
    ('NORMAL', 'FEMALE', 2000, 3, '지우'), ('NORMAL', 'FEMALE', 2000, 4, '서현'), ('NORMAL', 'FEMALE', 2000, 5, '민서'), ('NORMAL', 'FEMALE', 2000, 6, '하은'),
    ('NORMAL', 'FEMALE', 2000, 7, '하윤'), ('NORMAL', 'FEMALE', 2000, 8, '윤서'), ('NORMAL', 'FEMALE', 2000, 9, '지민'), ('NORMAL', 'FEMALE', 2000, 10, '채원'),
    ('NORMAL', 'FEMALE', 2000, 11, '수아'), ('NORMAL', 'FEMALE', 2000, 12, '지아'), ('NORMAL', 'FEMALE', 2000, 13, '예은'), ('NORMAL', 'FEMALE', 2000, 14, '다은'),
    ('NORMAL', 'FEMALE', 2000, 15, '소윤'), ('NORMAL', 'FEMALE', 2000, 16, '유진'), ('NORMAL', 'FEMALE', 2000, 17, '수빈'), ('NORMAL', 'FEMALE', 2000, 18, '예린'),
    ('NORMAL', 'FEMALE', 2000, 19, '지유'), ('NORMAL', 'FEMALE', 2000, 20, '시은'), ('NORMAL', 'FEMALE', 2000, 21, '채은'), ('NORMAL', 'FEMALE', 2000, 22, '지원'),
    ('NORMAL', 'FEMALE', 2000, 23, '하린'), ('NORMAL', 'FEMALE', 2000, 24, '예원'), ('NORMAL', 'FEMALE', 2000, 25, '수민'), ('NORMAL', 'FEMALE', 2000, 26, '나윤'),
    ('NORMAL', 'FEMALE', 2000, 27, '다인'), ('NORMAL', 'FEMALE', 2000, 28, '서영'), ('NORMAL', 'FEMALE', 2000, 29, '가은'), ('NORMAL', 'FEMALE', 2000, 30, '연우'),
    ('SINGLE', 'MALE', 0, 1, '훈'), ('SINGLE', 'MALE', 0, 2, '철'), ('SINGLE', 'MALE', 0, 3, '진'), ('SINGLE', 'MALE', 0, 4, '혁'),
    ('SINGLE', 'MALE', 0, 5, '준'), ('SINGLE', 'MALE', 0, 6, '범'), ('SINGLE', 'MALE', 0, 7, '현'), ('SINGLE', 'MALE', 0, 8, '웅'),
    ('SINGLE', 'MALE', 0, 9, '건'), ('SINGLE', 'MALE', 0, 10, '율'), ('SINGLE', 'FEMALE', 0, 1, '진'), ('SINGLE', 'FEMALE', 0, 2, '솔'),
    ('SINGLE', 'FEMALE', 0, 3, '별'), ('SINGLE', 'FEMALE', 0, 4, '혜'), ('SINGLE', 'FEMALE', 0, 5, '은'), ('SINGLE', 'FEMALE', 0, 6, '란'),
    ('SINGLE', 'FEMALE', 0, 7, '숙'), ('SINGLE', 'FEMALE', 0, 8, '희'), ('SINGLE', 'FEMALE', 0, 9, '윤'), ('SINGLE', 'FEMALE', 0, 10, '결'),
    ('NATIVE', 'ANY', 0, 1, '한별'), ('NATIVE', 'ANY', 0, 2, '나래'), ('NATIVE', 'ANY', 0, 3, '슬기'), ('NATIVE', 'ANY', 0, 4, '하늘'),
    ('NATIVE', 'ANY', 0, 5, '보람'), ('NATIVE', 'ANY', 0, 6, '다솜'), ('NATIVE', 'ANY', 0, 7, '아름'), ('NATIVE', 'ANY', 0, 8, '가람'),
    ('NATIVE', 'ANY', 0, 9, '새롬'), ('NATIVE', 'ANY', 0, 10, '한결'), ('NATIVE', 'ANY', 0, 11, '누리'), ('NATIVE', 'ANY', 0, 12, '한솔'),
    ('NATIVE', 'ANY', 0, 13, '빛나'), ('NATIVE', 'ANY', 0, 14, '이슬'), ('NATIVE', 'ANY', 0, 15, '단비'), ('TRIPLE', 'ANY', 0, 1, '빛나리'),
    ('TRIPLE', 'ANY', 0, 2, '한가람'), ('TRIPLE', 'ANY', 0, 3, '새로미'), ('TRIPLE', 'ANY', 0, 4, '이슬비'), ('TRIPLE', 'ANY', 0, 5, '누리솔')
), surname AS (
    SELECT s.name, SUM(s.w) OVER (ORDER BY s.name) - s.w AS lo, SUM(s.w) OVER (ORDER BY s.name) AS hi,
           SUM(s.w) OVER () AS total
      FROM (VALUES ('김', 21.5), ('이', 14.7), ('박', 8.4), ('최', 4.7), ('정', 4.3), ('강', 2.3), ('조', 2.1), ('윤', 2.1), ('장', 2.0), ('임', 1.6),
           ('한', 1.5), ('오', 1.5), ('서', 1.5), ('신', 1.5), ('권', 1.4), ('황', 1.4), ('안', 1.4), ('송', 1.3), ('류', 1.2), ('전', 1.1),
           ('홍', 1.1), ('고', 0.9), ('문', 0.9), ('양', 0.9), ('손', 0.9), ('배', 0.8), ('백', 0.8), ('허', 0.6), ('남', 0.6), ('노', 0.6),
           ('유', 0.6), ('심', 0.5), ('하', 0.5), ('곽', 0.4), ('성', 0.4), ('차', 0.4), ('주', 0.4), ('우', 0.4), ('구', 0.4), ('민', 0.3),
           ('진', 0.3), ('나', 0.3), ('지', 0.3), ('엄', 0.3), ('채', 0.3), ('원', 0.3), ('천', 0.2), ('방', 0.2), ('공', 0.2), ('현', 0.2),
           ('함', 0.2), ('변', 0.2), ('염', 0.2), ('여', 0.2), ('추', 0.1), ('도', 0.1), ('소', 0.1), ('석', 0.1), ('선', 0.1), ('설', 0.1),
           ('마', 0.1), ('길', 0.1), ('연', 0.1), ('위', 0.1), ('표', 0.1), ('명', 0.1), ('기', 0.1), ('반', 0.1), ('왕', 0.1), ('금', 0.1),
           ('옥', 0.1), ('육', 0.1), ('인', 0.1), ('맹', 0.1), ('제', 0.1), ('모', 0.1), ('탁', 0.05), ('국', 0.05), ('편', 0.05), ('피', 0.05),
           ('봉', 0.05), ('남궁', 0.3), ('황보', 0.3), ('제갈', 0.25), ('선우', 0.25), ('독고', 0.2), ('사공', 0.2), ('서문', 0.15)) AS s(name, w)
), base AS (
    SELECT u.id,
           CASE WHEN u.gender IN ('MALE', 'FEMALE') THEN u.gender
                WHEN random() < 0.5 THEN 'MALE' ELSE 'FEMALE' END AS gender,
           LEAST(GREATEST(COALESCE(EXTRACT(YEAR FROM u.birth_date)::int, 1975) / 10 * 10, 1940), 2000) AS decade,
           random() AS k, random() AS r, random() AS pick
      FROM tb_users u WHERE u.email LIKE '%@synthetic.invalid'
), picked AS (
    SELECT id, gender, decade, r, pick,
           CASE WHEN k < 0.03 THEN 'SINGLE'
                WHEN decade >= 1980 AND k < 0.07 THEN 'NATIVE'
                WHEN decade >= 1980 AND k < 0.08 THEN 'TRIPLE'
                ELSE 'NORMAL' END AS kind
      FROM base
)
UPDATE tb_users u
   SET name = s.name || g.name
  FROM picked p
  JOIN given g ON g.kind = p.kind
              AND g.gender = CASE WHEN p.kind IN ('NATIVE', 'TRIPLE') THEN 'ANY' ELSE p.gender END
              AND g.decade = CASE WHEN p.kind = 'NORMAL' THEN p.decade ELSE 0 END
              AND g.n = 1 + floor(p.pick * CASE p.kind WHEN 'NORMAL' THEN 30 WHEN 'SINGLE' THEN 10
                                                         WHEN 'NATIVE' THEN 15 ELSE 5 END)::int
  JOIN surname s ON p.r * s.total >= s.lo AND p.r * s.total < s.hi
 WHERE u.id = p.id;
