/* 저장 안 한 입력이 있을 때 나가기 확인 — 공지·콘텐츠·배너·약관 폼이 같이 쓴다.
 * 페이지는 guardSnapshot()(비교할 입력을 문자열로)을 갖추고, 불러오기·저장 뒤 markSaved() 를 부른다.
 * 라우트 가드는 각 페이지의 beforeRouteLeave 에서 confirmLeave(next) 를 부른다(믹스인에선 가드가 걸리지 않을 수 있다) */
import { confirmDialog } from '@/utils/confirm-dialog';

export default {
  data() {
    return { savedSnapshot: '', skipLeaveGuard: false };
  },
  mounted() {
    window.addEventListener('beforeunload', this.onBeforeUnloadGuard);
    // 처음 그린 값이 기준이다 — 아무것도 안 만졌는데 '변경됨'으로 잡히지 않게
    this.$nextTick(this.markSaved);
  },
  beforeUnmount() {
    window.removeEventListener('beforeunload', this.onBeforeUnloadGuard);
  },
  methods: {
    guardSnapshot() {
      return JSON.stringify(this.form ?? null);
    },
    markSaved() {
      this.savedSnapshot = this.guardSnapshot();
    },
    isDirty() {
      return this.guardSnapshot() !== this.savedSnapshot;
    },
    // 새로고침·탭 닫기는 브라우저 기본 확인창만 띄울 수 있다(문구 지정 불가)
    onBeforeUnloadGuard(event) {
      if (this.skipLeaveGuard || !this.isDirty()) return;
      event.preventDefault();
      event.returnValue = '';
    },
    async confirmLeave(next) {
      if (this.skipLeaveGuard || !this.isDirty()) {
        next();
        return;
      }
      const ok = await confirmDialog(this.$q, {
        title: '페이지 나가기',
        message: '저장하지 않은 내용이 있습니다. 나가면 사라집니다.',
        ok: '나가기',
        color: 'negative',
        cancel: '계속 작성',
      });
      next(ok);
    },
  },
};
