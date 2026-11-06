/*
 * 저장된 리치 텍스트(강의 소개·유의사항 등) 유틸.
 * 규칙: `v-html`에는 반드시 `sanitizeRichText()`를 거친 값만 바인딩한다. 화면별 예외 없음.
 */

/** q-editor 값은 HTML 문자열 — 태그를 벗긴 실제 입력 길이 */
export function plainTextLength(html) {
  return String(html || '')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim().length;
}

/**
 * q-editor 기본 toolbar (위저드·공지 작성 공용).
 * 정렬·들여쓰기는 style 속성이 필요한데 sanitize가 style을 전부 지워서 먹통이라 뺐다.
 * 나중에 필요해지면 `text-align` 값만 전체 문자열 정규식 일치로 좁게 열 것.
 */
export const EDITOR_TOOLBAR = [
  ['bold', 'italic', 'underline', 'strike'],
  ['link'],
  ['unordered', 'ordered'],
];

/* q-editor로 저장된 본문을 그대로 v-html에 넣으면 저장형 XSS가 된다.
   허용 태그 밖은 태그만 벗기고(내용은 텍스트로 남김) 속성은 화이트리스트만 남긴다. */
const ALLOWED_TAGS = new Set([
  'A',
  'B',
  'BLOCKQUOTE',
  'BR',
  'CODE',
  'DIV',
  'EM',
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
  'HR',
  'I',
  'IMG',
  'LI',
  'OL',
  'P',
  'PRE',
  'S',
  'SPAN',
  'STRIKE',
  'STRONG',
  'SUB',
  'SUP',
  'TABLE',
  'TBODY',
  'TD',
  'TH',
  'THEAD',
  'TR',
  'U',
  'UL',
]);
/* 자식이 텍스트 노드라 언랩하면 CSS·JS 소스가 본문에 글자로 남는다 — 통째로 지운다.
   (한글·워드에서 붙여넣으면 <style> 블록이 딸려온다) */
const DROP_TAGS = new Set([
  'SCRIPT',
  'STYLE',
  'TEMPLATE',
  'NOSCRIPT',
  'TITLE',
  'TEXTAREA',
  'XMP',
  'NOEMBED',
  'NOFRAMES',
]);
const HTML_NS = 'http://www.w3.org/1999/xhtml';
const ALLOWED_ATTRS = { A: ['href', 'title', 'target'], IMG: ['src', 'alt', 'title'] };
const URL_ATTRS = new Set(['href', 'src']);
const SAFE_SCHEME = /^(https?:|mailto:|tel:|data:image\/(png|jpe?g|gif|webp);)/i;

/* 브라우저는 URL을 해석하기 전에 탭·개행을 지우고 앞쪽 제어문자를 떼므로
   "java&#9;script:"·"\x01javascript:" 같은 값이 검사만 비켜 간다. 같은 규칙으로 정규화한 뒤 판정한다. */
function normalizeUrl(value) {
  const text = String(value).replace(/[\t\n\r]/g, '');
  let start = 0;
  while (start < text.length && text.charCodeAt(start) <= 0x20) start += 1;
  return text.slice(start);
}

/* 안전한 값만 남긴다 — 허용 스킴이거나, 콜론이 없는 상대경로·앵커·쿼리 */
function isSafeUrl(value) {
  const url = normalizeUrl(value);
  // '//host'는 콜론이 없어도 프로토콜 상대 URL이라 제3자 요청이 나간다
  return SAFE_SCHEME.test(url) || (!url.includes(':') && !url.startsWith('//'));
}

/** 저장된 HTML 본문 → v-html에 넣어도 되는 HTML */
export function sanitizeRichText(html) {
  const source = String(html || '');
  if (!source.trim() || typeof DOMParser === 'undefined') return '';
  const doc = new DOMParser().parseFromString(`<body>${source}</body>`, 'text/html');

  // 주석은 querySelectorAll에 안 잡히면서 innerHTML로는 나간다 (mXSS 창구)
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_COMMENT);
  const comments = [];
  while (walker.nextNode()) comments.push(walker.currentNode);
  comments.forEach((node) => node.remove());

  doc.body.querySelectorAll('*').forEach((el) => {
    // svg/mathml 안에서는 태그명이 소문자다 (<svg><script>) — 대문자로 맞춰 비교하고,
    // HTML 네임스페이스가 아닌 요소는 허용 목록과 무관하게 벗긴다
    const tag = el.tagName.toUpperCase();
    if (DROP_TAGS.has(tag)) {
      el.remove();
      return;
    }
    if (el.namespaceURI !== HTML_NS || !ALLOWED_TAGS.has(tag)) {
      el.replaceWith(...el.childNodes);
      return;
    }
    const keep = ALLOWED_ATTRS[tag] || [];
    Array.from(el.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase();
      const bad = !keep.includes(name) || (URL_ATTRS.has(name) && !isSafeUrl(attr.value));
      if (bad) el.removeAttribute(attr.name);
    });
    if (tag === 'A' && el.getAttribute('target')?.toLowerCase() === '_blank') {
      el.setAttribute('rel', 'noopener noreferrer');
    }
  });
  return doc.body.innerHTML;
}
