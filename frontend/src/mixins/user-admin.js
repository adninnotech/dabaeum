import { adminResetUserPassword, changeUserStatus } from '@/services/user-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { STATUS_LABEL } from '@/utils/status';
import { formatUserDisplayName } from '@/utils/user-display';

/** 회원 목록(강사·학생) 공통 배지 — 탈퇴는 회원 문맥에서만 gray라 전역 맵을 쓰지 않는다 */
export const USER_STATUS_BADGE = {
  ACTIVE: { label: STATUS_LABEL.ACTIVE, color: '#237a54' },
  DORMANT: { label: '비활성', color: '#a65300' },
  SUSPENDED: { label: '정지', color: '#c72656' },
  WITHDRAWN: { label: '탈퇴', color: '#727379' },
};

/** 바꾼 뒤 무슨 일이 생기는지 — 확인 창에 덧붙인다 */
const STATUS_EFFECT = {
  SUSPENDED: ' 정지된 계정은 로그인할 수 없습니다.',
  WITHDRAWN: ' 탈퇴 처리하면 로그인과 수강이 막힙니다.',
  DORMANT: ' 비활성 계정은 다시 활성으로 바꾸기 전까지 이용이 제한됩니다.',
};

export const USER_STATUS_FILTER = Object.entries(USER_STATUS_BADGE).map(([value, item]) => ({
  label: item.label,
  value,
}));

/** 상태 변경·비밀번호 초기화 (기존 관리자 페이지에서 이관) */
export default {
  data() {
    return {
      resetDialogOpen: false,
      resetTargetName: '',
      temporaryPassword: '',
    };
  },
  methods: {
    statusBadge(status) {
      return (
        USER_STATUS_BADGE[status] || { label: STATUS_LABEL[status] || status || '-', color: '' }
      );
    },
    /** 확인 창·알림에 쓰는 이름 — 'UUID' 원문이 보이던 것 대신 이름(생년월일) → 이메일 */
    memberLabel(row) {
      // formatUserDisplayName 은 이름이 없으면 UUID 를 돌려준다 — 이름이 없을 땐 이메일로
      if (row?.name || row?.userName) return formatUserDisplayName(row);
      return row?.email || '이 회원';
    },
    /**
     * 상태 변경 — 목록 ⋮·상세 [탈퇴 처리]가 함께 쓴다. 정지·탈퇴·비활성은 로그인과 수강을 막으므로
     * 누르는 즉시 바뀌던 것을 대상 이름과 결과를 적어 한 번 묻는다
     */
    async changeStatus(row, status) {
      if (!row?.id || status === row.status) return;
      const name = this.memberLabel(row);
      const label = USER_STATUS_BADGE[status]?.label || STATUS_LABEL[status] || status;
      const ok = await confirmDialog(this.$q, {
        title: `계정 상태를 '${label}'(으)로`,
        message: `${name}의 계정 상태를 '${label}'(으)로 바꿉니다.${STATUS_EFFECT[status] || ''}`,
        ok: '바꾸기',
        color: status === 'ACTIVE' ? 'primary' : 'negative',
        cancel: '닫기',
      });
      if (!ok) return;
      try {
        await changeUserStatus(row.id, status);
        this.$q.notify({ type: 'positive', message: `${name}의 계정을 '${label}'(으)로 바꿨습니다.`, position: 'top' });
        await this.load();
      } catch (error) {
        this.notifyError(error, '상태 변경에 실패했습니다.');
      }
    },
    /** 비밀번호 초기화 — 목록에서 누르면 바로 임시 비밀번호가 나갔다. 한 번 묻는다 */
    async resetPassword(row) {
      if (!row?.id) return;
      const name = this.memberLabel(row);
      const ok = await confirmDialog(this.$q, {
        title: '비밀번호 초기화',
        message: `${name}의 비밀번호를 임시 비밀번호로 바꿉니다. 지금 비밀번호로는 더 이상 로그인할 수 없습니다.`,
        ok: '초기화',
        color: 'negative',
        cancel: '닫기',
      });
      if (!ok) return;
      try {
        const result = await adminResetUserPassword(row.id);
        this.resetTargetName = name;
        this.temporaryPassword = result?.temporaryPassword || '';
        this.resetDialogOpen = true;
      } catch (error) {
        this.notifyError(error, '비밀번호 초기화에 실패했습니다.');
      }
    },
  },
};
