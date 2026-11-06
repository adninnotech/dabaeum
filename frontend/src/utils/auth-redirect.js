/*
 * 로그인·다대구 가입 뒤 돌아갈 주소(redirect).
 * - 같은 사이트 경로만 받는다('/…', '//host' 나 'https://…' 는 버린다) — 주소창에 넣은 값으로 밖으로 보내지 않게
 * - need=1 은 '로그인이 필요해서 왔다'는 표시 — 머리 [로그인]은 redirect 만 붙어 안내 문구를 띄우지 않는다
 */

export function safeRedirect(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text.startsWith('/') && !text.startsWith('//') && !text.startsWith('/\\') ? text : '';
}

/** 로그인이 필요한 동작에서 로그인 화면으로 — 가입 안내가 함께 뜬다 */
export function loginRequiredRoute(redirect) {
  const target = safeRedirect(redirect);
  return { path: '/login', query: target ? { redirect: target, need: '1' } : { need: '1' } };
}

