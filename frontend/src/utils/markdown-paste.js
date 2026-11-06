/*
 * q-editor 에 마크다운 글(README·챗봇 답변·메모 앱)을 붙여 넣으면 `## 제목`, `**굵게**` 가 글자 그대로 남았다.
 * 붙여넣을 때만 마크다운을 HTML 로 바꿔 넣는다 — 저장·표시 규칙(sanitizeRichText)은 그대로다.
 */
import { marked } from 'marked';
import { sanitizeRichText } from '@/utils/rich-text';

/* 줄 머리 문법(제목·목록·인용·코드펜스·표) 또는 줄 안 문법(굵게·링크·인라인 코드) */
const BLOCK_SYNTAX = /^(#{1,6}\s+\S|\s*[-*+]\s+\S|\s*\d+[.)]\s+\S|>\s?\S|```|\|.+\|\s*$)/m;
const INLINE_SYNTAX = /(\*\*[^*\n]+\*\*|__[^_\n]+__|\[[^\]\n]+\]\([^)\s]+\)|`[^`\n]+`)/;
/* 웹·노션·워드에서 복사하면 클립보드에 구조가 있는 HTML 이 같이 온다 — 그때는 Quasar 기본 붙여넣기에 맡긴다.
   VS Code·메모장 류는 div/span 만 든 HTML 을 주므로 이 검사에 걸리지 않는다 */
const STRUCTURED_HTML = /<(h[1-6]|ul|ol|table|blockquote|strong|b|em|a)[\s>]/i;

export function looksLikeMarkdown(text) {
  const source = String(text || '');
  return BLOCK_SYNTAX.test(source) || INLINE_SYNTAX.test(source);
}

/** 붙여넣을 HTML — 마크다운으로 볼 이유가 없으면 null (기본 붙여넣기) */
export function markdownPasteHtml(clipboardData) {
  if (!clipboardData) return null;
  const text = clipboardData.getData('text/plain');
  if (!looksLikeMarkdown(text)) return null;
  if (STRUCTURED_HTML.test(clipboardData.getData('text/html'))) return null;
  const html = sanitizeRichText(marked.parse(text, { gfm: true, breaks: true, async: false }));
  return html.trim() ? html : null;
}

/** q-editor `@paste` 핸들러 — 편집 영역(contenteditable)에 바로 넣어 q-editor 의 input 갱신을 그대로 탄다 */
export function pasteMarkdownAsRichText(event) {
  const html = markdownPasteHtml(event?.clipboardData);
  if (!html) return;
  event.preventDefault();
  document.execCommand('insertHTML', false, html);
}
