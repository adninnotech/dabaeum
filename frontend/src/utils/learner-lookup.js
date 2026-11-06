/**
 * 기관 대리 수강신청 — 학습자를 이름 + 생년월일 + 휴대전화로 찾는다(중앙 #56, #142).
 * POST /institutions/{id}/learners/lookup 은 셋이 모두 맞는 1명만 돌려준다(없음·불일치·여러 명 모두 404).
 * 평생학번은 없어졌고 다대구 회원은 이메일이 없어, 다대구 인증으로 받은 세 값으로 찾는다. 보내기 전에 형식을 거른다.
 * (@/ import 없이 — node --test 로 단위 테스트한다)
 */

const NAME_MAX = 100;
// 휴대전화 01X + 7~8자리. 서버도 숫자만 비교한다
const MOBILE = /^01[016789][0-9]{7,8}$/;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

const ERROR = {
  name: `이름은 ${NAME_MAX}자까지 입력할 수 있습니다.`,
  phone: '휴대전화 번호를 확인해 주세요.',
  birthDate: '생년월일을 YYYY-MM-DD 로 입력해 주세요.',
};

/** 'YYYY-MM-DD' 가 실제 있는 날이고 오늘(UTC 기준 넉넉히) 이전인지 */
function isPastDate(value) {
  const match = ISO_DATE.exec(value);
  if (!match) return false;
  const [, y, m, d] = match.map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) return false;
  return date.getTime() <= Date.now();
}

/**
 * 입력 { name, birthDate, phone } → { query, error }. query 는 lookup 요청 본문, 못 보내면 null.
 * 하나라도 비어 있으면 오류 문구 없이 null — [찾기] 버튼만 막는다. 휴대전화는 공백·하이픈을 지운 숫자로 보낸다
 */
export function lookupQuery({ name, birthDate, phone } = {}) {
  const trimmedName = String(name ?? '').trim();
  const birth = String(birthDate ?? '').trim();
  const digits = String(phone ?? '').replace(/[\s-]/g, '');
  if (!trimmedName || !birth || !digits) return { query: null, error: '' };
  if (trimmedName.length > NAME_MAX) return { query: null, error: ERROR.name };
  if (!isPastDate(birth)) return { query: null, error: ERROR.birthDate };
  if (!MOBILE.test(digits)) return { query: null, error: ERROR.phone };
  return { query: { name: trimmedName, birthDate: birth, phone: digits }, error: '' };
}

/**
 * 429 Retry-After(초) → '45초'·'2분'·'1시간 30분'. 같은 휴대전화 번호는 하루 10회까지라 24시간 기다려야 할 수 있어
 * 초로만 보이면 읽을 수 없었다. 없거나 0 이면 ''
 */
export function retryAfterText(seconds) {
  const total = Number(seconds);
  if (!Number.isFinite(total) || total <= 0) return '';
  if (total < 60) return `${Math.ceil(total)}초`;
  const minutes = Math.ceil(total / 60);
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours}시간 ${rest}분` : `${hours}시간`;
}
