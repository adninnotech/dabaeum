import { apiRequest, unwrapData } from '@/services/api-client';

/* CMS 메뉴·콘텐츠 페이지(설계서 §7). 관리 경로는 PLATFORM_ADMIN */

function asList(payload) {
  const list = unwrapData(payload);
  return Array.isArray(list) ? list : [];
}

// 공개 메뉴는 관리자가 바꿀 때만 바뀐다 — 페이지 이동마다 부르지 않도록 성공한 응답을 잡아 둔다
let publicMenusCache = null;

/** 공개 내비게이션 — 숨김·하위·미게시 페이지를 가리키는 메뉴는 서버가 뺐다. 인증 없이 호출한다 */
export async function listPublicMenus() {
  if (publicMenusCache) return publicMenusCache;
  publicMenusCache = asList(await apiRequest('/menus', { token: null }));
  return publicMenusCache;
}

/** 공개 콘텐츠 페이지 — 없거나 초안이면 404 CONTENT_PAGE_NOT_FOUND */
export async function getPublicContentPage(slug) {
  const payload = await apiRequest(`/contents/pages/${encodeURIComponent(slug)}`, { token: null });
  return unwrapData(payload);
}

/* ---------- 메뉴 관리 ---------- */

/** 숨김 포함 2단계 트리 */
export async function listAdminMenus() {
  return asList(await apiRequest('/admin/menus'));
}

/** ROUTE 메뉴 선택지 [{key}] — 키를 경로로 바꾸는 표는 프론트(utils/site-menu)가 가진다 */
export async function listMenuRoutes() {
  return asList(await apiRequest('/admin/menus/routes'));
}

export async function createMenu(body) {
  publicMenusCache = null;
  return unwrapData(await apiRequest('/admin/menus', { method: 'POST', body }));
}

export async function updateMenu(menuId, body) {
  publicMenusCache = null;
  return unwrapData(await apiRequest(`/admin/menus/${menuId}`, { method: 'PUT', body }));
}

/** 하위가 있으면 409 SITE_MENU_HAS_CHILDREN */
export async function deleteMenu(menuId) {
  publicMenusCache = null;
  await apiRequest(`/admin/menus/${menuId}`, { method: 'DELETE' });
}

/** 전체 배치 [{id, parentId, sortOrder}] → 새 트리. 3단계·모르는 id 는 422 이고 아무것도 안 바뀐다 */
export async function reorderMenus(items) {
  publicMenusCache = null;
  return asList(await apiRequest('/admin/menus/order', { method: 'PUT', body: items }));
}

/* ---------- 콘텐츠 페이지 관리 ---------- */

/** 초안 포함 목록(본문 없음) */
export async function listAdminContentPages() {
  return asList(await apiRequest('/admin/content-pages'));
}

export async function getAdminContentPage(pageId) {
  return unwrapData(await apiRequest(`/admin/content-pages/${pageId}`));
}

/** 생성은 DRAFT. slug 중복은 409 CONTENT_PAGE_CONFLICT */
export async function createContentPage(body) {
  return unwrapData(await apiRequest('/admin/content-pages', { method: 'POST', body }));
}

export async function updateContentPage(pageId, body) {
  publicMenusCache = null;
  return unwrapData(await apiRequest(`/admin/content-pages/${pageId}`, { method: 'PUT', body }));
}

/** 메뉴가 가리키는 페이지는 409 CONTENT_PAGE_IN_USE */
export async function deleteContentPage(pageId) {
  await apiRequest(`/admin/content-pages/${pageId}`, { method: 'DELETE' });
}

export async function setContentPagePublished(pageId, published) {
  publicMenusCache = null;
  const action = published ? 'publish' : 'unpublish';
  return unwrapData(await apiRequest(`/admin/content-pages/${pageId}/${action}`, { method: 'POST' }));
}
