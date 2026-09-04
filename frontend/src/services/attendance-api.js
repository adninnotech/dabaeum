import { apiRequest, asPage, createIdempotencyKey, unwrapData } from '@/services/api-client';

export async function issueAttendanceQrToken(sessionId) {
  const payload = await apiRequest(`/sessions/${sessionId}/qr-token`, {
    method: 'POST',
  });
  return unwrapData(payload);
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
