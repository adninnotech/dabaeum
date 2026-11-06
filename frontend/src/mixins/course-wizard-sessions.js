/* 강의 등록 위저드 — 반복 일정으로 회차 만들기·회차 추가/삭제/순서. form.sessions 를 다룬다 */
import { confirmDialog } from '@/utils/confirm-dialog';

/** 지운 회차를 되돌릴 수 있는 시간 */
const UNDO_TIMEOUT_MS = 6000;

/** 로컬 기준 YYYY-MM-DD. toISOString()은 KST에서 하루 밀린다 */
function toDateString(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function emptySession(sessionNo) {
  return { sessionNo, date: '', startTime: '', endTime: '', topic: '' };
}

/** 무언가 적어 둔 회차 — 이런 회차를 지울 때만 되돌리기를 준다 */
function hasInput(session) {
  return Boolean(session.date || session.startTime || session.endTime || session.topic?.trim());
}

const WEEKDAY_TEXT = ['일', '월', '화', '수', '목', '금', '토'];
// 월요일부터 읽히게(일요일은 맨 뒤)
const WEEKDAY_ORDER = [1, 2, 3, 4, 5, 6, 0];
// 반복 일정 칸을 고치는 동안 회차를 매번 다시 만들지 않게 잠깐 기다린다
const AUTO_GENERATE_DELAY_MS = 400;

function repeatComplete(repeat) {
  return Boolean(
    repeat.weekdays.length && repeat.startTime && repeat.endTime && repeat.firstDate && Number(repeat.count) > 0,
  );
}

/** 'YYYY-MM-DD' 의 하루 전 */
function dayBefore(dateString) {
  const date = new Date(`${dateString}T00:00:00`);
  date.setDate(date.getDate() - 1);
  return toDateString(date);
}

/** '10. 15.' */
function shortDate(dateString) {
  const [, month, day] = dateString.split('-');
  return `${Number(month)}. ${Number(day)}.`;
}

export default {
  data() {
    return {
      dragFrom: null,
      // 자동으로 만든 회차·모집 기간 — 강사가 손대지 않은 동안만 일정이 바뀌면 다시 맞춘다
      autoSessionsJson: '',
      autoRecruit: { start: '', end: '' },
      autoGenerateTimer: null,
    };
  },
  computed: {
    /** '매주 월·수 14:00~16:00 · 10. 15.~11. 19. · 6회' — 회차가 있으면 한 줄 요약 */
    scheduleSummary() {
      const dated = this.form.sessions.filter((session) => session.date);
      if (!dated.length) return '';
      const dates = dated.map((session) => session.date).sort();
      const { weekdays, startTime, endTime } = this.form.repeat;
      const days = WEEKDAY_ORDER.filter((day) => weekdays.includes(day)).map((day) => WEEKDAY_TEXT[day]);
      const repeatText =
        this.form.scheduleMode === 'REPEAT' && days.length && startTime && endTime
          ? `매주 ${days.join('·')} ${startTime}~${endTime} · `
          : '';
      const range = dates.length > 1 ? `${shortDate(dates[0])}~${shortDate(dates.at(-1))}` : shortDate(dates[0]);
      return `${repeatText}${range} · ${this.form.sessions.length}회`;
    },
  },
  watch: {
    // 요일·시간·첫 수업일·회차가 다 차면 [일정 생성]을 누르지 않아도 회차를 만든다
    'form.repeat': {
      deep: true,
      handler() {
        window.clearTimeout(this.autoGenerateTimer);
        this.autoGenerateTimer = window.setTimeout(() => this.autoGenerateSessions(), AUTO_GENERATE_DELAY_MS);
      },
    },
  },
  beforeUnmount() {
    window.clearTimeout(this.autoGenerateTimer);
  },
  methods: {
    /** 반복 일정으로 회차 자동 생성 — 회차가 비었거나 자동으로 만든 그대로일 때만(강사가 고친 회차는 덮지 않는다) */
    autoGenerateSessions() {
      if (this.form.scheduleMode !== 'REPEAT' || !repeatComplete(this.form.repeat)) return;
      const untouched = !this.form.sessions.length || JSON.stringify(this.form.sessions) === this.autoSessionsJson;
      if (!untouched) return;
      this.applyGeneratedSessions(this.buildRepeatSessions());
    },
    applyGeneratedSessions(sessions) {
      this.form.sessions = sessions;
      this.autoSessionsJson = JSON.stringify(sessions);
      this.fillRecruitDates();
    },
    /**
     * 모집 기간 자동 채우기 — 시작 = 오늘, 마감 = 첫 수업 전날(오늘보다 앞이면 첫 수업일).
     * 비었거나 자동으로 넣은 값 그대로일 때만 바꾼다(강사가 고친 날짜는 둔다)
     */
    fillRecruitDates() {
      const first = this.form.sessions.map((session) => session.date).filter(Boolean).sort()[0];
      if (!first) return;
      const today = toDateString(new Date());
      const start = today <= first ? today : first;
      const before = dayBefore(first);
      const end = before >= start ? before : first;
      const f = this.form;
      if (!f.recruitStartDate || f.recruitStartDate === this.autoRecruit.start) f.recruitStartDate = start;
      if (!f.recruitEndDate || f.recruitEndDate === this.autoRecruit.end) f.recruitEndDate = end;
      this.autoRecruit = { start, end };
    },
    toggleWeekday(value) {
      const { weekdays } = this.form.repeat;
      this.form.repeat.weekdays = weekdays.includes(value)
        ? weekdays.filter((day) => day !== value)
        : [...weekdays, value];
    },
    buildRepeatSessions() {
      const { weekdays, startTime, endTime, firstDate, count } = this.form.repeat;
      const out = [];
      const cursor = new Date(`${firstDate}T00:00:00`);
      // 하루씩 전진하는 단순 순회. count*7+7일이면 어떤 요일 조합이든 반드시 채워진다
      const maxDays = Number(count) * 7 + 7;
      for (let i = 0; i < maxDays && out.length < Number(count); i += 1) {
        if (weekdays.includes(cursor.getDay())) {
          out.push({ ...emptySession(out.length + 1), date: toDateString(cursor), startTime, endTime });
        }
        cursor.setDate(cursor.getDate() + 1);
      }
      return out;
    },
    async onGenerate() {
      const { weekdays, startTime, endTime, firstDate, count } = this.form.repeat;
      if (!weekdays.length || !startTime || !endTime || !firstDate || !(Number(count) > 0)) {
        this.$q.notify({ type: 'warning', message: '반복 요일·시간·첫 수업일·총 회차를 모두 입력해 주세요.' });
        return;
      }
      const existing = this.form.sessions.length;
      if (existing) {
        // 몇 회차가 사라지고 몇 회차가 생기는지 숫자로 — '덮어씁니다'만으로는 크기를 몰랐다
        const ok = await confirmDialog(this.$q, {
          title: '회차 다시 만들기',
          message: `지금 있는 ${existing}회차를 지우고 ${Number(count)}회차를 새로 만듭니다.`,
          ok: '다시 만들기',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      this.applyGeneratedSessions(this.buildRepeatSessions());
    },
    addSession() {
      this.form.sessions = [...this.form.sessions, emptySession(this.form.sessions.length + 1)];
    },
    /** 적어 둔 회차가 아이콘 한 번에 사라졌다 — 지운 뒤 [되돌리기]를 준다(매번 묻는 창보다 덜 번거롭다) */
    removeSession(index) {
      const removed = this.form.sessions[index];
      if (!removed) return;
      this.form.sessions = this.form.sessions.filter((_, i) => i !== index);
      this.renumber();
      if (!hasInput(removed)) return;
      this.$q.notify({
        message: `${removed.sessionNo}회차를 지웠습니다.`,
        // 아래에 띄우면 하단 [다음 단계]를 가렸다
        position: 'top',
        timeout: UNDO_TIMEOUT_MS,
        actions: [{ label: '되돌리기', color: 'yellow', handler: () => this.restoreSession(index, removed) }],
      });
    },
    restoreSession(index, session) {
      const next = [...this.form.sessions];
      next.splice(Math.min(index, next.length), 0, session);
      this.form.sessions = next;
      this.renumber();
    },
    renumber() {
      this.form.sessions.forEach((session, i) => {
        session.sessionNo = i + 1;
      });
    },
    onSessionDragStart(index) {
      this.dragFrom = index;
    },
    moveSession(to) {
      if (this.dragFrom === null || this.dragFrom === to) return;
      const next = [...this.form.sessions];
      const [row] = next.splice(this.dragFrom, 1);
      next.splice(to, 0, row);
      this.form.sessions = next;
      this.dragFrom = null;
      this.renumber();
    },
    // 위/아래 버튼 — 드래그를 못 쓰는 터치·키보드 사용자용. 번호는 드래그와 같은 renumber 로 맞춘다
    moveSessionBy(index, delta) {
      const to = index + delta;
      if (to < 0 || to >= this.form.sessions.length) return;
      const next = [...this.form.sessions];
      [next[index], next[to]] = [next[to], next[index]];
      this.form.sessions = next;
      this.renumber();
    },
  },
};
