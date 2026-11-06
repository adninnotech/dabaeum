import { listCourseClassifications } from '@/services/classification-api';

/*
 * 강좌 분야 메뉴(머리 전체 메뉴 패널·☰ 전체 메뉴 창) 공통 — 표준 강좌 분류 트리를 받아 6칸과 아래 줄로 나눈다.
 * 트리는 classification-api 가 모듈에 잡아 두어 두 메뉴가 같이 써도 한 번만 받는다
 */
export default {
  data() {
    return { tree: [], treeRequest: null };
  },
  computed: {
    // 하위가 있는 대분류 — 6칸의 각 열
    columns() {
      return this.tree.filter((node) => node.children?.length);
    },
    // 하위가 없는 대분류는 열이 비어 보여 아래 줄로 뺀다
    singles() {
      return this.tree.filter((node) => !node.children?.length);
    },
  },
  methods: {
    // 겹쳐 불러도 한 번만 받는다. 실패하면 다음에 열 때 다시 받는다
    loadTree() {
      if (!this.treeRequest) {
        this.treeRequest = listCourseClassifications()
          .then((tree) => {
            this.tree = tree;
          })
          .catch((error) => {
            console.warn('[course-classification-menu] 강좌 분류를 불러오지 못했습니다', error);
            this.tree = [];
            this.treeRequest = null;
          });
      }
      return this.treeRequest;
    },
    // 강좌찾기 화면이 읽는 주소 — tab=대분류 이름, sub=중분류 코드
    tabRoute(root, middle) {
      const query = middle ? { tab: root.name, sub: middle.code } : { tab: root.name };
      return { path: '/courses', query };
    },
  },
};
