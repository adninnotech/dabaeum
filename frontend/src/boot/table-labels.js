import { defineBoot } from '#q-app';
import { Lang } from 'quasar';

/* q-table 아래 줄 문구 — Quasar 한국어 기본값('페이지 당 개수', '2 중 1-2')이 다른 콘솔 목록
   ('10개씩 보기', '총 N건 중')과 달랐다. 표마다 고치던 것을 앱 전체에서 한 번에 맞춘다 */
export default defineBoot(() => {
  Lang.props.table.recordsPerPage = '한 번에 보기';
  Lang.props.table.pagination = (start, end, total) => `총 ${total}건 중 ${start}-${end}`;
});
