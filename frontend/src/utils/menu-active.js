/** 사이드바 메뉴 활성 여부 (exact / prefix / query / none) */
export function isMenuPathActive(route, item) {
  if (!item || item.match === 'none') return false;

  const currentPath = route.path || '';
  const tab = route.query?.tab;
  const to = String(item.to || '');
  const pathPart = to.split('?')[0];
  const queryPart = to.includes('?') ? to.slice(to.indexOf('?') + 1) : '';
  const tabFromTo = new URLSearchParams(queryPart).get('tab');

  if (tabFromTo) {
    return currentPath === pathPart && tab === tabFromTo;
  }

  if (Array.isArray(item.exclude) && item.exclude.some((prefix) => currentPath.startsWith(prefix))) {
    return false;
  }

  if (item.match === 'home') {
    return currentPath === '/' || currentPath.startsWith('/courses');
  }

  if (item.match === 'exact') {
    if (pathPart === '/learning') return currentPath === '/learning' && !tab;
    return currentPath === pathPart;
  }

  return currentPath === pathPart || currentPath.startsWith(`${pathPart}/`);
}
