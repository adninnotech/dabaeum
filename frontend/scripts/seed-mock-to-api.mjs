/**
 * mockApi.js 데이터를 실 API(DB)에 적재하는 시드 스크립트
 *
 * 사용: node scripts/seed-mock-to-api.mjs
 *
 * 환경변수(선택):
 *   API_BASE=http://<api-host>:8080/api/v1
 *   API_TOKEN=<bearer-token>
 */

const API_BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api/v1';
const API_TOKEN = process.env.API_TOKEN || '';

const institutionSeed = {
  institutionCode: 'SEOUL-LLC',
  name: '서울평생교육원',
  businessNumber: '123-45-67890',
  representativeName: '김대표',
  address: '서울특별시 중구 세종대로 110',
  contactPhone: '02-1234-5678',
  contactEmail: 'admin@seoul-llc.kr',
  status: 'ACTIVE',
};

const usersSeed = [
  {
    key: 'hong',
    name: '가나다',
    email: 'learner@dabaeum.kr',
    phone: '010-1111-2222',
    birthDate: '1990-01-15',
    status: 'ACTIVE',
    roles: ['LEARNER'],
  },
  {
    key: 'kim',
    name: '김영희',
    email: 'kim@example.com',
    phone: '010-9876-5432',
    birthDate: '1988-05-20',
    status: 'ACTIVE',
    roles: ['LEARNER'],
  },
  {
    key: 'instructor',
    name: '강사입니다',
    email: 'instructor@dabaeum.kr',
    phone: '010-3333-4444',
    birthDate: '1980-11-02',
    status: 'ACTIVE',
    roles: ['INSTRUCTOR'],
  },
  {
    key: 'institution-admin',
    name: '기관관리자',
    email: 'institution@dabaeum.kr',
    phone: '010-5555-6666',
    birthDate: '1975-07-07',
    status: 'ACTIVE',
    roles: ['INSTITUTION_ADMIN'],
  },
  {
    key: 'platform-admin',
    name: '사이트관리자',
    email: 'admin@dabaeum.kr',
    phone: '010-7777-8888',
    birthDate: '1970-03-03',
    status: 'ACTIVE',
    roles: ['PLATFORM_ADMIN'],
  },
];

const coursesSeed = [
  {
    key: 'c1',
    courseCode: 'IT-DA-101',
    title: '데이터 분석 기초와 실무',
    description: '파이썬 기반 데이터 분석 기초부터 실무 리포팅까지 학습합니다.',
    category: 'IT/디지털',
    educationType: 'ONLINE',
    startDate: '2026-09-01',
    endDate: '2026-10-31',
    recruitStartDate: '2026-07-01',
    recruitEndDate: '2026-08-31',
    capacity: 30,
    location: '온라인',
    onlineUrl: 'https://learn.example.com/c1',
    creditBankEligible: true,
    creditValue: 3,
    publish: true,
  },
  {
    key: 'c2',
    courseCode: 'EDU-PAR-201',
    title: '부모교육: 아이와 함께하는 대화법',
    description: '가정 내 소통을 돕는 부모교육 프로그램입니다.',
    category: '교육/직무',
    educationType: 'OFFLINE',
    startDate: '2026-09-10',
    endDate: '2026-10-20',
    recruitStartDate: '2026-07-15',
    recruitEndDate: '2026-08-20',
    capacity: 25,
    location: '부산광역시',
    onlineUrl: null,
    creditBankEligible: false,
    creditValue: null,
    publish: true,
  },
  {
    key: 'c3',
    courseCode: 'IT-AI-301',
    title: '인공지능 이해와 활용',
    description: '생성형 AI와 업무 활용 사례를 중심으로 학습합니다.',
    category: 'IT/디지털',
    educationType: 'HYBRID',
    startDate: '2026-09-15',
    endDate: '2026-11-30',
    recruitStartDate: '2026-08-01',
    recruitEndDate: '2026-09-05',
    capacity: 40,
    location: '대구광역시 + 온라인',
    onlineUrl: 'https://learn.example.com/c3',
    creditBankEligible: true,
    creditValue: 2,
    publish: true,
  },
  {
    key: 'c4',
    courseCode: 'HUM-201',
    title: '인문학으로 읽는 현대사회',
    description: '현대사회 이슈를 인문학적 시각으로 살펴봅니다.',
    category: '인문교양',
    educationType: 'OFFLINE',
    startDate: '2026-08-25',
    endDate: '2026-09-20',
    recruitStartDate: '2026-07-20',
    recruitEndDate: '2026-08-18',
    capacity: 35,
    location: '광주광역시',
    onlineUrl: null,
    creditBankEligible: false,
    creditValue: null,
    publish: true,
  },
  {
    key: 'c5',
    courseCode: 'ART-101',
    title: '수채화로 그리는 일상',
    description: '수채화 기초 기법과 일상 스케치를 연습합니다.',
    category: '문화/예술',
    educationType: 'OFFLINE',
    startDate: '2026-08-20',
    endDate: '2026-10-15',
    recruitStartDate: '2026-07-10',
    recruitEndDate: '2026-08-12',
    capacity: 20,
    location: '인천광역시',
    onlineUrl: null,
    creditBankEligible: false,
    creditValue: null,
    publish: true,
  },
  {
    key: 'c6',
    courseCode: 'HLTH-110',
    title: '시니어 건강관리와 웰빙',
    description: '시니어를 위한 생활 건강관리와 웰빙 습관을 배웁니다.',
    category: '건강/웰빙',
    educationType: 'HYBRID',
    startDate: '2026-09-05',
    endDate: '2026-10-10',
    recruitStartDate: '2026-08-01',
    recruitEndDate: '2026-08-28',
    capacity: 40,
    location: '대전광역시',
    onlineUrl: 'https://learn.example.com/c6',
    creditBankEligible: false,
    creditValue: null,
    publish: true,
  },
];

const sessionsSeed = [
  {
    courseKey: 'c1',
    sessionNo: 1,
    startsAt: '2026-09-01T19:00:00+09:00',
    endsAt: '2026-09-01T21:00:00+09:00',
    location: '온라인 Zoom',
    attendanceOpensAt: '2026-09-01T18:45:00+09:00',
    attendanceClosesAt: '2026-09-01T19:30:00+09:00',
    status: 'SCHEDULED',
  },
  {
    courseKey: 'c1',
    sessionNo: 2,
    startsAt: '2026-09-08T19:00:00+09:00',
    endsAt: '2026-09-08T21:00:00+09:00',
    location: '온라인 Zoom',
    attendanceOpensAt: '2026-09-08T18:45:00+09:00',
    attendanceClosesAt: '2026-09-08T19:30:00+09:00',
    status: 'SCHEDULED',
  },
  {
    courseKey: 'c1',
    sessionNo: 3,
    startsAt: '2026-08-11T19:00:00+09:00',
    endsAt: '2026-08-11T21:00:00+09:00',
    location: '온라인 Zoom',
    attendanceOpensAt: '2026-08-11T18:45:00+09:00',
    attendanceClosesAt: '2026-08-11T19:30:00+09:00',
    status: 'OPEN',
  },
];

const enrollmentsSeed = [
  { courseKey: 'c1', userKey: 'hong', type: 'SELF', approve: true },
  { courseKey: 'c2', userKey: 'hong', type: 'SELF', approve: false },
  { courseKey: 'c1', userKey: 'kim', type: 'PROXY', approve: false },
];

const log = (...args) => console.log(...args);
const warn = (...args) => console.warn(...args);

async function api(path, { method = 'GET', body, query } = {}) {
  const url = new URL(`${API_BASE}${path.startsWith('/') ? path : `/${path}`}`);
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    });
  }
  const res = await fetch(url, {
    method,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${API_TOKEN}`,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  if (!res.ok) {
    const message =
      (payload && (payload.message || payload.error || payload.title)) ||
      `HTTP ${res.status}`;
    const err = new Error(message);
    err.status = res.status;
    err.payload = payload;
    throw err;
  }
  return payload?.data !== undefined ? payload.data : payload;
}

async function listAll(path, query = {}) {
  const data = await api(path, { query: { page: 0, size: 100, ...query } });
  return Array.isArray(data) ? data : [];
}

async function ensureInstitution() {
  const existing = await listAll('/institutions');
  const found = existing.find((i) => i.institutionCode === institutionSeed.institutionCode);
  if (found) {
    log(`[skip] institution ${found.institutionCode} → ${found.id}`);
    return found;
  }
  try {
    const created = await api('/institutions', { method: 'POST', body: institutionSeed });
    log(`[ok] institution created ${created.institutionCode} → ${created.id}`);
    return created;
  } catch (error) {
    warn(`[warn] institution create failed: ${error.message}`);
    const again = await listAll('/institutions');
    const fallback = again[0];
    if (!fallback) throw error;
    log(`[fallback] use institution ${fallback.institutionCode} → ${fallback.id}`);
    return fallback;
  }
}

async function ensureUsers(institutionId) {
  const existing = await listAll('/users', { sort: 'createdAt,desc' });
  const byEmail = new Map(
    existing.filter((u) => u.email).map((u) => [String(u.email).toLowerCase(), u]),
  );
  const map = {};

  for (const seed of usersSeed) {
    let user = byEmail.get(seed.email.toLowerCase());
    if (!user) {
      try {
        user = await api('/users', {
          method: 'POST',
          body: {
            name: seed.name,
            email: seed.email,
            phone: seed.phone,
            birthDate: seed.birthDate,
            status: seed.status,
          },
        });
        log(`[ok] user created ${seed.email} → ${user.id}`);
      } catch (error) {
        warn(`[warn] user create ${seed.email}: ${error.message}`);
        const refreshed = await listAll('/users', { sort: 'createdAt,desc' });
        user = refreshed.find((u) => String(u.email || '').toLowerCase() === seed.email.toLowerCase());
        if (!user) throw error;
        log(`[skip] user found after conflict ${seed.email} → ${user.id}`);
      }
    } else {
      log(`[skip] user exists ${seed.email} → ${user.id}`);
    }

    map[seed.key] = user;

    let roles = [];
    try {
      roles = (await api(`/users/${user.id}/roles`)) || [];
    } catch {
      roles = [];
    }
    const have = new Set(
      (Array.isArray(roles) ? roles : []).map((r) => (typeof r === 'string' ? r : r.role)),
    );
    for (const role of seed.roles) {
      if (have.has(role)) {
        log(`[skip] role ${role} on ${seed.email}`);
        continue;
      }
      // INSTRUCTOR / INSTITUTION_ADMIN 는 institutionId 필요
      const needsInstitution = role === 'INSTRUCTOR' || role === 'INSTITUTION_ADMIN';
      try {
        await api(`/users/${user.id}/roles`, {
          method: 'POST',
          body: {
            role,
            institutionId: needsInstitution ? institutionId : null,
          },
        });
        log(`[ok] role ${role} → ${seed.email}`);
      } catch (error) {
        warn(`[warn] role ${role} on ${seed.email}: ${error.message}`);
      }
    }
  }
  return map;
}

async function ensureCourses(institutionId) {
  const existing = await listAll('/courses', { sort: 'createdAt,desc' });
  const byCode = new Map(existing.map((c) => [c.courseCode, c]));
  const map = {};

  for (const seed of coursesSeed) {
    let course = byCode.get(seed.courseCode);
    if (!course) {
      const body = {
        institutionId,
        courseCode: seed.courseCode,
        title: seed.title,
        description: seed.description,
        category: seed.category,
        educationType: seed.educationType,
        startDate: seed.startDate,
        endDate: seed.endDate,
        recruitStartDate: seed.recruitStartDate,
        recruitEndDate: seed.recruitEndDate,
        capacity: seed.capacity,
        location: seed.location,
        onlineUrl: seed.onlineUrl,
        creditBankEligible: seed.creditBankEligible,
        creditValue: seed.creditValue,
      };
      try {
        course = await api('/courses', { method: 'POST', body });
        log(`[ok] course created ${seed.courseCode} → ${course.id}`);
      } catch (error) {
        warn(`[warn] course create ${seed.courseCode}: ${error.message}`);
        const refreshed = await listAll('/courses', { sort: 'createdAt,desc' });
        course = refreshed.find((c) => c.courseCode === seed.courseCode);
        if (!course) throw error;
      }
    } else {
      log(`[skip] course exists ${seed.courseCode} → ${course.id}`);
    }

    if (seed.publish && course.status === 'DRAFT') {
      try {
        course = await api(`/courses/${course.id}/publish`, { method: 'POST' });
        log(`[ok] course published ${seed.courseCode}`);
      } catch (error) {
        warn(`[warn] publish ${seed.courseCode}: ${error.message}`);
      }
    }
    map[seed.key] = course;
  }
  return map;
}

async function ensureSessions(courseMap) {
  const map = {};
  for (const seed of sessionsSeed) {
    const course = courseMap[seed.courseKey];
    if (!course) continue;
    const sessions = await listAll(`/courses/${course.id}/sessions`);
    let session = sessions.find((s) => s.sessionNo === seed.sessionNo);
    if (!session) {
      try {
        session = await api(`/courses/${course.id}/sessions`, {
          method: 'POST',
          body: {
            sessionNo: seed.sessionNo,
            startsAt: seed.startsAt,
            endsAt: seed.endsAt,
            location: seed.location,
            attendanceOpensAt: seed.attendanceOpensAt,
            attendanceClosesAt: seed.attendanceClosesAt,
            status: seed.status,
          },
        });
        log(`[ok] session ${seed.courseKey}#${seed.sessionNo} → ${session.id}`);
      } catch (error) {
        warn(`[warn] session ${seed.courseKey}#${seed.sessionNo}: ${error.message}`);
        continue;
      }
    } else {
      log(`[skip] session ${seed.courseKey}#${seed.sessionNo} → ${session.id}`);
      if (seed.status && session.status !== seed.status) {
        try {
          session = await api(`/sessions/${session.id}`, {
            method: 'PUT',
            body: { status: seed.status },
          });
          log(`[ok] session status → ${seed.status}`);
        } catch (error) {
          warn(`[warn] session status: ${error.message}`);
        }
      }
    }
    map[`${seed.courseKey}:${seed.sessionNo}`] = session;
  }
  return map;
}

async function ensureEnrollments(courseMap, userMap) {
  const results = [];
  for (const seed of enrollmentsSeed) {
    const course = courseMap[seed.courseKey];
    const user = userMap[seed.userKey];
    if (!course || !user) continue;

    const list = await listAll(`/courses/${course.id}/enrollments`);
    let enrollment = list.find((e) => e.userId === user.id);
    if (!enrollment) {
      try {
        // 테스트 토큰은 SELF 수강신청이 403일 수 있어 PROXY 우선
        enrollment = await api(`/courses/${course.id}/proxy-enrollments`, {
          method: 'POST',
          body: { userId: user.id },
        });
        log(`[ok] enrollment(proxy) ${seed.userKey}@${seed.courseKey} → ${enrollment.id}`);
      } catch (error) {
        try {
          enrollment = await api(`/courses/${course.id}/enrollments`, {
            method: 'POST',
            body: { userId: user.id, applicationType: 'SELF' },
          });
          log(`[ok] enrollment(self) ${seed.userKey}@${seed.courseKey} → ${enrollment.id}`);
        } catch (selfError) {
          warn(`[warn] enrollment ${seed.userKey}@${seed.courseKey}: ${error.message} / ${selfError.message}`);
          const again = await listAll(`/courses/${course.id}/enrollments`);
          enrollment = again.find((e) => e.userId === user.id);
          if (!enrollment) continue;
        }
      }
    } else {
      log(`[skip] enrollment ${seed.userKey}@${seed.courseKey} → ${enrollment.id}`);
    }

    if (seed.approve && enrollment.status === 'APPLIED') {
      try {
        enrollment = await api(`/enrollments/${enrollment.id}/approve`, { method: 'POST' });
        log(`[ok] enrollment approved ${enrollment.id}`);
      } catch (error) {
        warn(`[warn] approve ${enrollment.id}: ${error.message}`);
      }
    }
    results.push(enrollment);
  }
  return results;
}

async function ensureSampleAttendance(sessionMap, enrollments, userMap, courseMap) {
  const session = sessionMap['c1:3'];
  const hong = userMap.hong;
  const course = courseMap.c1;
  if (!session || !hong || !course) return;

  const enrollment = enrollments.find((e) => e.userId === hong.id && e.courseId === course.id);
  if (!enrollment || enrollment.status !== 'APPROVED') {
    warn('[skip] attendance: need APPROVED enrollment for hong@c1');
    return;
  }

  try {
    const list = await listAll(`/sessions/${session.id}/attendance`);
    if (list.some((a) => a.enrollmentId === enrollment.id)) {
      log('[skip] attendance already exists for hong@c1 session3');
      return;
    }
  } catch (error) {
    warn(`[warn] list attendance: ${error.message}`);
  }

  try {
    const created = await api(`/sessions/${session.id}/attendance`, {
      method: 'POST',
      body: {
        enrollmentId: enrollment.id,
        attendanceMethod: 'ADMIN',
        status: 'PRESENT',
        checkedAt: '2026-08-11T19:05:00+09:00',
        source: 'ADMIN_WEB',
      },
    });
    log(`[ok] attendance created → ${created.id}`);
  } catch (error) {
    warn(`[warn] attendance create: ${error.message}`);
  }
}

async function main() {
  log(`API_BASE=${API_BASE}`);
  log('=== seed mock → API start ===');

  const ping = await api('/system/ping', {}).catch((e) => {
    throw new Error(`ping 실패: ${e.message}`);
  });
  log(`[ok] ping`, ping);

  const institution = await ensureInstitution();
  const userMap = await ensureUsers(institution.id);
  const courseMap = await ensureCourses(institution.id);
  const sessionMap = await ensureSessions(courseMap);
  const enrollments = await ensureEnrollments(courseMap, userMap);
  await ensureSampleAttendance(sessionMap, enrollments, userMap, courseMap);

  log('=== seed complete ===');
  log('요약:');
  log(`  institution: ${institution.id}`);
  log(
    `  users: ${Object.entries(userMap)
      .map(([k, u]) => `${k}=${u.id}`)
      .join(', ')}`,
  );
  log(
    `  courses: ${Object.entries(courseMap)
      .map(([k, c]) => `${k}=${c.id}`)
      .join(', ')}`,
  );
}

main().catch((error) => {
  console.error('SEED FAILED:', error.message);
  if (error.payload) console.error(JSON.stringify(error.payload, null, 2));
  process.exit(1);
});
