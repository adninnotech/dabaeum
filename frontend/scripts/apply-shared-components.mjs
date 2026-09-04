/**
 * 페이지 공통 컴포넌트 일괄 적용 (StatusBadge, AppErrorBanner)
 * 사용: node scripts/apply-shared-components.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pagesDir = path.join(root, 'src', 'pages');

const badgePatterns = [
  [
    /<q-badge :color="statusColor\[props\.row\.status\] \|\| 'grey'">\{\{ props\.row\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="props.row.status" />',
  ],
  [
    /<q-badge :color="statusColor\[props\.value\] \|\| 'grey'">\{\{ props\.value \}\}<\/q-badge>/g,
    '<StatusBadge :status="props.value" />',
  ],
  [
    /<q-badge :color="statusColor\[props\.row\.status\] \|\| 'grey'">\{\{ props\.row\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="props.row.status" />',
  ],
  [
    /<q-badge :color="statusColor\[course\.status\] \|\| 'grey'">\{\{ course\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="course.status" />',
  ],
  [
    /<q-badge :color="statusColor\[session\.status\] \|\| 'grey'">\{\{ session\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="session.status" />',
  ],
  [
    /<q-badge :color="statusColor\[item\.status\] \|\| 'grey'">\{\{ item\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="item.status" />',
  ],
  [
    /<q-badge :color="statusColor\[enrollment\.status\] \|\| 'grey'">\s*\{\{ enrollment\.status \}\}\s*<\/q-badge>/g,
    '<StatusBadge :status="enrollment.status" />',
  ],
  [
    /<q-badge :color="statusColor\[completion\.status\] \|\| 'grey'">\{\{ completion\.status \}\}<\/q-badge>/g,
    '<StatusBadge :status="completion.status" />',
  ],
  [
    /<q-badge :color="statusColor\[credential\.status\] \|\| 'grey'">\s*\{\{ credential\.status \}\}\s*<\/q-badge>/g,
    '<StatusBadge :status="credential.status" />',
  ],
];

const errorPatterns = [
  [
    /<q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md">\s*\{\{ errorMessage \}\}\s*<\/q-banner>/g,
    '<AppErrorBanner :message="errorMessage" />',
  ],
  [
    /<q-banner v-if="errorMessage" dense class="bg-red-1 text-negative q-mb-md">\s*\{\{ errorMessage \}\}\s*<\/q-banner>/g,
    '<AppErrorBanner :message="errorMessage" />',
  ],
];

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(full) : entry.name.endsWith('.vue') ? [full] : [];
  });
}

function ensureImport(content, importLine) {
  if (content.includes(importLine)) return content;
  const scriptIdx = content.indexOf('<script>');
  if (scriptIdx === -1) return content;
  const insertAt = content.indexOf('\n', scriptIdx) + 1;
  return `${content.slice(0, insertAt)}${importLine}\n${content.slice(insertAt)}`;
}

function ensureComponent(content, name) {
  if (new RegExp(`components:\\s*\\{[^}]*\\b${name}\\b`).test(content)) return content;
  const match = content.match(/components:\s*\{/);
  if (!match) {
    return content.replace(
      /export default \{\n(\s+)name:/,
      `export default {\n$1components: { ${name} },\n$1name:`,
    );
  }
  return content.replace(/components:\s*\{/, `components: { ${name}, `);
}

function cleanupStatusColor(content) {
  let next = content.replace(/\nimport \{ statusColor \} from '@\/utils\/status';/g, '');
  next = next.replace(/\nimport \{ statusColor, ([^}]+)\} from '@\/utils\/status';/g, "\nimport { $1 } from '@/utils/status';");
  next = next.replace(/,\s*statusColor/g, '');
  next = next.replace(/statusColor,\s*/g, '');
  next = next.replace(/\n\s*statusColor,\n/g, '\n');
  next = next.replace(/\n\s*return \{\n\s*statusColor,\n/g, '\n    return {\n');
  return next;
}

let changed = 0;
for (const file of walk(pagesDir)) {
  let content = fs.readFileSync(file, 'utf8');
  const original = content;

  for (const [pattern, replacement] of errorPatterns) {
    content = content.replace(pattern, replacement);
  }
  for (const [pattern, replacement] of badgePatterns) {
    content = content.replace(pattern, replacement);
  }

  const usesStatusBadge = content.includes('<StatusBadge');
  const usesErrorBanner = content.includes('<AppErrorBanner');

  if (usesStatusBadge) {
    content = ensureImport(content, "import StatusBadge from '@/components/StatusBadge.vue';");
    content = ensureComponent(content, 'StatusBadge');
    content = cleanupStatusColor(content);
  }
  if (usesErrorBanner) {
    content = ensureImport(content, "import AppErrorBanner from '@/components/AppErrorBanner.vue';");
    content = ensureComponent(content, 'AppErrorBanner');
  }

  if (content !== original) {
    fs.writeFileSync(file, content, 'utf8');
    changed += 1;
    console.log(`updated ${path.relative(root, file)}`);
  }
}

console.log(`done: ${changed} files`);
