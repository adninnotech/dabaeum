import { apiRequest, asPage, unwrapData } from '@/services/api-client';

// ⚠ /notices·/admin/notices 둘 다 계약에 Sort 파라미터가 없다(§3) — sort를 받지 않는다.
export async function listNotices({ page = 0, size = 20, audience } = {}) {
  const payload = await apiRequest('/notices', {
    query: { page, size, audience },
    token: null,
  });
  return asPage(payload);
}

export async function getNotice(noticeId) {
  const payload = await apiRequest(`/notices/${noticeId}`, { token: null });
  return unwrapData(payload);
}

/** 관리자 공지 상세 — 초안·예약 공지도 온다(공개 GET /notices/{id} 는 게시된 것만). 플랫폼 관리자 전용 */
export async function getAdminNotice(noticeId) {
  const payload = await apiRequest(`/admin/notices/${noticeId}`);
  return unwrapData(payload);
}

export async function listAdminNotices({ page = 0, size = 20, q, audience, status } = {}) {
  const payload = await apiRequest('/admin/notices', {
    query: { page, size, q, audience, status },
  });
  return asPage(payload);
}

export async function createNotice(body) {
  const payload = await apiRequest('/admin/notices', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateNotice(noticeId, body) {
  const payload = await apiRequest(`/admin/notices/${noticeId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

export async function deleteNotice(noticeId) {
  await apiRequest(`/admin/notices/${noticeId}`, { method: 'DELETE' });
}

/** 게시된 공지의 첨부 — 인증 없이 부른다. 초안·예약 게시 전이면 404 NOTICE_NOT_FOUND */
export async function listPublicNoticeAttachments(noticeId) {
  const payload = await apiRequest(`/notices/${noticeId}/attachments`);
  const rows = unwrapData(payload);
  return Array.isArray(rows) ? [...rows].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)) : [];
}

export async function listNoticeAttachments(noticeId) {
  const payload = await apiRequest(`/admin/notices/${noticeId}/attachments`);
  return unwrapData(payload) || [];
}

// ⚠ NoticeAttachmentAttachRequest는 additionalProperties: false로 fileId 단독만 받는다
// (CourseAttachmentAttachRequest와 같은 모양) — fileName·sortOrder를 같이 보내면 422.
export async function attachNoticeFile(noticeId, { fileId }) {
  const payload = await apiRequest(`/admin/notices/${noticeId}/attachments`, {
    method: 'POST',
    body: { fileId },
  });
  return unwrapData(payload);
}

export async function detachNoticeFile(noticeId, attachmentId) {
  await apiRequest(`/admin/notices/${noticeId}/attachments/${attachmentId}`, { method: 'DELETE' });
}
