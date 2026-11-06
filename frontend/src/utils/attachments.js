import { ATTACHMENT_ACCEPT, ATTACHMENT_MAX_BYTES } from '@/config/course-options';

/**
 * 확장자·용량으로 첨부 후보를 거른다.
 * @returns {{ accepted: File[], rejected: string[] }} rejected는 "파일명: 사유" 문자열
 */
export function pickAttachments(
  files,
  { accept = ATTACHMENT_ACCEPT, maxBytes = ATTACHMENT_MAX_BYTES } = {},
) {
  const allowed = accept.split(',');
  const accepted = [];
  const rejected = [];
  Array.from(files || []).forEach((file) => {
    const ext = `.${file.name.split('.').pop().toLowerCase()}`;
    if (!allowed.includes(ext)) rejected.push(`${file.name}: 형식`);
    else if (file.size > maxBytes) rejected.push(`${file.name}: 용량`);
    else accepted.push(file);
  });
  return { accepted, rejected };
}
