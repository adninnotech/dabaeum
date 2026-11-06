/**
 * 콘솔 표 정렬 — 칸 정의에 `sortable: 'date' | 'number' | 'text'` 를 달면 머리글을 눌러 정렬한다.
 * 화살표만으론 ↑가 최신인지 헷갈려 '최신순'·'오래된순'처럼 글자로 함께 보인다.
 */
export const SORT_LABELS = {
  date: { desc: '최신순', asc: '오래된순' },
  number: { desc: '많은순', asc: '적은순' },
  text: { asc: '가나다순', desc: '가나다 역순' },
};

/** 처음 누를 때 방향 — 날짜·숫자는 최신·많은 것부터, 글자는 가나다순 */
const FIRST_DIR = { date: 'desc', number: 'desc', text: 'asc' };

export function sortLabel(type, dir) {
  return SORT_LABELS[type]?.[dir] || '';
}

/** 같은 칸이면 방향을 뒤집고, 다른 칸이면 그 칸의 첫 방향으로 */
export function nextSort(column, current) {
  if (current?.key === column.key) {
    return { key: column.key, dir: current.dir === 'desc' ? 'asc' : 'desc' };
  }
  return { key: column.key, dir: FIRST_DIR[column.sortable] || 'asc' };
}

function sortValue(value, type) {
  if (value == null || value === '' || value === '-') return null;
  if (type === 'date') {
    const time = new Date(value).getTime();
    return Number.isNaN(time) ? null : time;
  }
  if (type === 'number') {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  return String(value);
}

/** 정렬한 새 배열 — 값이 없는 행(로그인 기록 없음 등)은 방향과 상관없이 맨 아래 */
export function sortRows(rows, key, type, dir) {
  if (!key || !type) return rows;
  const sign = dir === 'asc' ? 1 : -1;
  return rows
    .map((row, index) => ({ row, index, value: sortValue(row[key], type) }))
    .sort((a, b) => {
      if (a.value === null || b.value === null) {
        if (a.value === b.value) return a.index - b.index;
        return a.value === null ? 1 : -1;
      }
      const diff = type === 'text' ? a.value.localeCompare(b.value, 'ko') : a.value - b.value;
      return diff ? diff * sign : a.index - b.index;
    })
    .map((item) => item.row);
}
