import { API_BASE_URL } from '@/config/api';
import { apiDownload, apiFormDataRequest, unwrapData } from '@/services/api-client';

/** StoredFile.url → 브라우저 표시용 절대 URL */
export function resolveStoredFileUrl(url) {
  if (!url) return '';
  // 저장 전 미리보기(URL.createObjectURL)·data URL 은 그대로
  if (/^(https?|blob|data):/i.test(url)) return url;
  const origin = typeof window !== 'undefined' ? window.location.origin : '';
  if (url.startsWith('/')) return `${origin}${url}`;
  const base = API_BASE_URL.startsWith('http')
    ? API_BASE_URL.replace(/\/$/, '')
    : `${origin}${API_BASE_URL.replace(/\/$/, '')}`;
  return `${base}/${url.replace(/^\//, '')}`;
}

/**
 * 저장 파일 본문 URL.
 * 주의: 선행 슬래시를 붙이면 안 된다. '/files/...' 는 resolveStoredFileUrl 의 절대경로 분기를 타서
 * origin 바로 뒤에 붙고 API_BASE_URL('/api/v1')이 빠진다. 그 주소는 개발 서버의 SPA 폴백에
 * 걸려 200 + text/html 을 돌려주므로 이미지가 조용히 깨진다(상태코드만 보면 정상으로 보인다).
 */
export function storedFileContentUrl(fileId) {
  return fileId ? resolveStoredFileUrl(`files/${fileId}/content`) : '';
}

// purpose enum (FileUploadRequest): PROFILE, COURSE_THUMBNAIL, COURSE_ATTACHMENT,
// INSTITUTION_LOGO, NOTICE_ATTACHMENT, BANNER, BADGE_IMAGE
export async function downloadStoredFile(fileId, fallbackName = 'attachment') {
  const { blob, filename } = await apiDownload(`/files/${fileId}/content`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || fallbackName;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function uploadFile(file, purpose = 'PROFILE') {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('purpose', purpose);
  const payload = await apiFormDataRequest('/files', { method: 'POST', formData });
  const data = unwrapData(payload);
  if (data?.url) {
    return { ...data, url: resolveStoredFileUrl(data.url) };
  }
  return data;
}
