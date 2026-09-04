/**
 * 페이지 제목/부제목 스타일 통일
 * 사용: node scripts/apply-page-header-style.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const pagesDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'pages');

const skipFiles = new Set([
  'LoginPage.vue',
  'SignupPage.vue',
  'SignupFormPage.vue',
  'SignupCompletePage.vue',
  'ErrorNotFound.vue',
]);

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    return entry.name.endsWith('.vue') ? [full] : [];
  });
}

function normalize(content) {
  let next = content;

  next = next.replace(
    /<div class="row items-center justify-between q-mb-(?:md|lg)">/g,
    '<div class="app-page-header row items-center justify-between q-mb-lg">',
  );

  next = next.replace(
    /(<div class="app-page-header row items-center justify-between q-mb-lg">\s*<div>\s*)<div class="text-h5 text-weight-bold">/g,
    '$1<div class="app-page-header__title text-h5 text-weight-bold">',
  );

  next = next.replace(
    /(<div class="app-page-header__title text-h5 text-weight-bold">[\s\S]*?<\/div>\s*)<div class="text-grey-7">/g,
    '$1<div class="app-page-header__subtitle text-grey-7">',
  );

  next = next.replace(
    /<div class="text-h4 text-weight-bold(?: q-mb-lg)?">/g,
    '<div class="app-page-header__title text-h5 text-weight-bold">',
  );

  next = next.replace(
    /(<div class="app-page-header__title text-h5 text-weight-bold">[\s\S]*?<\/div>\s*)<div class="text-grey-7 q-mb-lg">/g,
    '$1<div class="app-page-header__subtitle text-grey-7 q-mb-lg">',
  );

  next = next.replace(
    /(<q-page padding>\s*)<div class="text-h5 text-weight-bold">([^<]+)<\/div>\s*<div class="text-grey-7([^"]*)">([^<]*)<\/div>/g,
    '$1<div class="app-page-header q-mb-lg"><div class="app-page-header__title text-h5 text-weight-bold">$2</div><div class="app-page-header__subtitle text-grey-7$3">$4</div></div>',
  );

  next = next.replace(
    /(<div class="page-inner">\s*)<div class="text-h5 text-weight-bold">([^<]+)<\/div>\s*<div class="text-grey-7([^"]*)">([^<]*)<\/div>/g,
    '$1<div class="app-page-header q-mb-lg"><div class="app-page-header__title text-h5 text-weight-bold">$2</div><div class="app-page-header__subtitle text-grey-7$3">$4</div></div>',
  );

  return next;
}

for (const filePath of walk(pagesDir)) {
  if (skipFiles.has(path.basename(filePath))) continue;
  const original = fs.readFileSync(filePath, 'utf8');
  const updated = normalize(original);
  if (updated !== original) {
    fs.writeFileSync(filePath, updated);
    console.log('updated', path.relative(pagesDir, filePath));
  }
}
