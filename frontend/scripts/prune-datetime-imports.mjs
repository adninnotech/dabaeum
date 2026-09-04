/**
 * @/utils/datetime import에서 실제 사용하는 심볼만 남김
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src');

const symbols = [
  'formatDateKst',
  'formatDatePeriodKst',
  'formatDateRangeKst',
  'formatDateTimeFields',
  'formatDateTimeKst',
  'qFormatDateKst',
  'qFormatDateTimeKst',
];

const importRe = /import\s+\{([^}]+)\}\s+from\s+'@\/utils\/datetime';/;

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    return entry.name.endsWith('.vue') || entry.name.endsWith('.js') ? [full] : [];
  });
}

function isUsed(content, name, importLine) {
  const body = content.replace(importLine, '');
  const patterns = [
    new RegExp(`\\b${name}\\b`),
    new RegExp(`\\$${name}\\b`),
  ];
  if (name.startsWith('format')) {
    patterns.push(new RegExp(`\\$${name.replace(/^format/, 'format')}\\b`));
  }
  if (name === 'formatDateTimeKst') patterns.push(/\$formatDateTimeKst\b/);
  if (name === 'formatDateKst') patterns.push(/\$formatDateKst\b/);
  if (name === 'formatDatePeriodKst') patterns.push(/\$formatDatePeriodKst\b/);
  if (name === 'formatDateRangeKst') patterns.push(/\$formatDateRangeKst\b/);
  return patterns.some((re) => re.test(body));
}

for (const filePath of walk(root)) {
  const content = fs.readFileSync(filePath, 'utf8');
  const match = content.match(importRe);
  if (!match) continue;

  const importLine = match[0];
  const used = symbols.filter((name) => isUsed(content, name, importLine));
  let next = content;

  if (used.length === 0) {
    next = content.replace(`${importLine}\n`, '');
  } else {
    const newImport = `import { ${used.join(', ')} } from '@/utils/datetime';`;
    next = content.replace(importLine, newImport);
  }

  if (next !== content) {
    fs.writeFileSync(filePath, next);
    console.log(path.relative(root, filePath), '->', used.join(', ') || '(removed)');
  }
}
