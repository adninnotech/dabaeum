/**
 * 콘솔 목록 공통 상태 — 클라이언트 검색·필터·페이지.
 * TODO(backend): 목록 API에 `q`가 없는 화면(배너·카테고리)은 클라이언트에서 거른다.
 * `q`가 있는 목록은 페이지에서 `serverSearch: true`를 켜고 `load()`에 `q`를 실어 서버 검색으로
 * 넘긴다 — 서버 상한 100건 밖의 데이터도 검색된다.
 * 페이지는 `searchFields`(문자열 비교 대상 키 배열)만 정의하면 된다.
 * 값이 같은지로 못 거르는 필터는 `matchFilter(row, key, value)`(true/false, 모르는 키는 undefined),
 * 선택 목록 밖 조건(날짜 칸)은 `matchRow(row)` 메서드로 더한다.
 */
import { sortRows } from '@/utils/table-sort';

export default {
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      page: 1,
      pageSize: 10,
      search: { q: '', filters: {} },
      searchFields: [],
      /** 서버가 알려 준 전체 건수 — 받아 온 rows 보다 많으면 숫자 카드가 '받은 것만' 센 값임을 밝힌다 */
      serverTotal: null,
      /** true 면 검색을 서버에 맡긴다(load()가 q를 실어 재조회). q가 없는 목록은 기본값 false 유지 */
      serverSearch: false,
      /** 머리글 정렬 — { key, dir }. 칸 정의에 sortable 이 있는 칸만. key 가 비면 받은 순서 그대로 */
      sort: { key: '', dir: 'desc' },
    };
  },
  computed: {
    filteredRows() {
      // 서버가 이미 걸렀으면 키워드로 다시 거르지 않는다 (필터는 그대로 적용)
      const keyword = this.serverSearch ? '' : (this.search.q || '').toLowerCase();
      const filters = this.search.filters || {};
      return this.rows.filter((row) => {
        for (const [key, value] of Object.entries(filters)) {
          if (value == null || value === '') continue;
          // 페이지가 matchFilter 로 직접 판단하는 필터(기간·구간 등) — undefined 면 값 비교로 넘긴다
          const custom = typeof this.matchFilter === 'function' ? this.matchFilter(row, key, value) : undefined;
          if (custom !== undefined) {
            if (!custom) return false;
            continue;
          }
          if (row[key] !== value) return false;
        }
        // 선택 목록 밖의 조건(날짜 칸 등) — 페이지가 matchRow 를 두면 그것까지 통과해야 한다
        if (typeof this.matchRow === 'function' && !this.matchRow(row)) return false;
        if (!keyword) return true;
        return this.searchFields.some((field) =>
          String(row[field] ?? '')
            .toLowerCase()
            .includes(keyword),
        );
      });
    },
    sortedRows() {
      const column = (this.columns || []).find((item) => item.key === this.sort.key);
      return sortRows(this.filteredRows, this.sort.key, column?.sortable, this.sort.dir);
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.sortedRows.slice(start, start + this.pageSize);
    },
  },
  methods: {
    /** 목록 조회 실패는 배너로 — 빈 상태는 정상 0건일 때만 보여야 한다 */
    handleLoadError(error, fallback = '목록을 불러오지 못했습니다.') {
      this.errorMessage = error?.message || fallback;
      this.rows = [];
    },
    onSearch(payload) {
      this.search = payload;
      this.page = 1;
    },
    onSort(sort) {
      this.sort = sort;
      this.page = 1;
    },
    /** 서버 검색 모드면 재조회, 아니면 첫 페이지로만 이동. `load()`는 페이지가 정의한다 */
    applyServerSearch() {
      this.page = 1;
      if (this.serverSearch && typeof this.load === 'function') return this.load();
      return undefined;
    },
    /** 페이지 응답의 totalElements 를 기억한다 */
    rememberTotal(page) {
      const total = page?.page?.totalElements;
      this.serverTotal = Number.isFinite(total) ? total : null;
    },
    /**
     * 숫자 카드 이름 — 100건만 받아 세면서 '전체 강사 수'라고 했다.
     * 받은 행이 전체보다 적으면 '(최근 N건 기준)'을 붙인다
     */
    statLabel(label) {
      if (this.serverTotal == null || this.serverTotal <= this.rows.length) return label;
      return `${label} (최근 ${this.rows.length}건 기준)`;
    },
    countBy(key, value) {
      return this.rows.filter((row) => row[key] === value).length;
    },
    notifyError(error, fallback = '요청에 실패했습니다.') {
      this.$q.notify({ type: 'negative', message: error?.message || fallback, position: 'top' });
    },
  },
};
