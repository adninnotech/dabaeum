/* CMS 메뉴 routeKey → 프론트 경로. 키 목록은 백엔드(GET /admin/menus/routes)가, 경로는 프론트가 정한다 */
export const MENU_ROUTES = {
  HOME: { path: '/', label: '홈' },
  COURSES: { path: '/courses', label: '강좌 찾기' },
  POPULAR_COURSES: { path: '/courses/popular', label: '인기 강좌' },
  SEARCH: { path: '/search', label: '통합검색' },
  // TODO(ui): 학습자용 기관 목록 화면이 없다 — 기관 이름으로 찾을 수 있는 강좌 찾기로 보낸다
  INSTITUTIONS: { path: '/courses', label: '기관 (강좌 찾기로 연결)' },
  NOTICES: { path: '/notices', label: '공지사항' },
  FAQS: { path: '/support', label: '자주 묻는 질문' },
  INQUIRY: { path: '/support', label: '문의하기 (고객센터)' },
  BADGES: { path: '/badges', label: '배지 도감' },
  MYPAGE: { path: '/mypage', label: '마이페이지' },
  PORTFOLIO: { path: '/wallet/portfolio', label: '학습 포트폴리오' },
  // 이메일 가입이 없어졌다(#112) — 다대구 로그인이 처음 온 사람의 가입까지 맡는다. 키는 백엔드 목록에 남아 있다
  SIGNUP: { path: '/login', label: '회원가입 (다대구 로그인으로 연결)' },
};

export const LINK_TYPE_LABEL = {
  ROUTE: '사이트 화면',
  CONTENT: '콘텐츠 페이지',
  EXTERNAL: '외부 링크',
};

export function contentPagePath(slug) {
  return `/pages/${encodeURIComponent(slug)}`;
}

/** 메뉴 노드 → { path } 또는 { href, external } (갈 곳이 없으면 null) */
export function menuTarget(node) {
  if (node?.linkType === 'EXTERNAL' && node.externalUrl) {
    return { href: node.externalUrl, external: true };
  }
  if (node?.linkType === 'CONTENT' && node.contentSlug) {
    return { path: contentPagePath(node.contentSlug) };
  }
  if (node?.linkType === 'ROUTE' && MENU_ROUTES[node.routeKey]) {
    return { path: MENU_ROUTES[node.routeKey].path };
  }
  return null;
}

/** 관리 화면용 한 줄 설명 — "사이트 화면 · 인기 강좌", "콘텐츠 페이지 · /pages/about" */
export function menuTargetText(node) {
  if (node.linkType === 'ROUTE') return MENU_ROUTES[node.routeKey]?.label || node.routeKey || '-';
  if (node.linkType === 'CONTENT') return node.contentSlug ? contentPagePath(node.contentSlug) : '(페이지 없음)';
  return node.externalUrl || '-';
}
