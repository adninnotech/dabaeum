// 비운 칸은 '' 대신 null — 서버가 빈 문자열을 그대로 저장해 다른 화면·DB 값(null)과 어긋난다(백엔드 #44 확인)
const blankToNull = (value) => (typeof value === 'string' && value.trim() ? value.trim() : null);

/** 기관 관리자가 PUT /institutions/{id} 로 바꿀 수 있는 네 필드만 — 그 밖의 필드를 보내면 403 */
export function institutionProfileUpdate({ contactPhone, contactEmail, introduction, logoFileId }) {
  return {
    contactPhone: blankToNull(contactPhone),
    contactEmail: blankToNull(contactEmail),
    introduction: blankToNull(introduction),
    // null 은 '로고 지우기'(필드가 있으면 그 값으로 저장)
    logoFileId: logoFileId || null,
  };
}
