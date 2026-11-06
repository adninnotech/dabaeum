import { apiRequest, asPage, createIdempotencyKey, unwrapData } from '@/services/api-client';

/** 201 { token, expiresAt, attendUrl }. QR 에는 attendUrl 을 그대로 넣는다 (프론트가 조립하지 않음).
 * ttlSeconds 는 서버 시각(meta.timestamp) 기준 남은 시간 — PC 시계가 틀려도 카운트다운이 맞도록 덧붙인다. */
export async function issueAttendanceQrToken(sessionId) {
  const payload = await apiRequest(`/sessions/${sessionId}/qr-token`, {
    method: 'POST',
  });
  const data = unwrapData(payload);
  const serverNow = Date.parse(payload?.meta?.timestamp);
  const expiresAt = Date.parse(data?.expiresAt);
  const ttlSeconds =
    Number.isFinite(serverNow) && Number.isFinite(expiresAt)
      ? Math.max(0, Math.round((expiresAt - serverNow) / 1000))
      : null;
  return { ...data, ttlSeconds };
}

export async function listSessionAttendance(sessionId, { page = 0, size = 50, sort } = {}) {
  const payload = await apiRequest(`/sessions/${sessionId}/attendance`, {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function recordAttendance(sessionId, body) {
  const payload = await apiRequest(`/sessions/${sessionId}/attendance`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function getAttendance(attendanceId) {
  const payload = await apiRequest(`/attendance/${attendanceId}`);
  return unwrapData(payload);
}

export async function adjustAttendance(attendanceId, body) {
  try {
    const payload = await apiRequest(`/attendance/${attendanceId}`, {
      method: 'PATCH',
      body,
    });
    return unwrapData(payload);
  } catch (error) {
    if (error.status !== 405 && error.status !== 404) throw error;
    const payload = await apiRequest(`/attendance/${attendanceId}`, {
      method: 'PUT',
      body,
    });
    return unwrapData(payload);
  }
}

export async function getAttendanceSummary(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/attendance-summary`);
  return unwrapData(payload);
}

export { createIdempotencyKey };

/** 학생 다대구 출석 세션 열기 (무인증 · local/dev 전용). token 은 QR 주소의 t 값 그대로.
 * 201 { attendId, state: 'WAITING', deepLink }. 409 ATTENDANCE_QR_INVALID/EXPIRED, 503 다대구 발급 실패 */
export async function openAttendSession(token) {
  const payload = await apiRequest('/attend-sessions', {
    method: 'POST',
    token: null,
    body: { token },
  });
  return unwrapData(payload);
}

/** 학생 다대구 출석 결과 (무인증). 앱에서 돌아온 순간·확인 버튼에서만 부른다 — 타이머 폴링 금지
 * (서버가 부를 때마다 다대구에 묻는다). 404 ATTEND_SESSION_NOT_FOUND = 없거나 만료 뒤 정리됨 */
export async function getAttendSession(attendId) {
  const payload = await apiRequest(`/attend-sessions/${attendId}`, { token: null });
  return unwrapData(payload);
}
