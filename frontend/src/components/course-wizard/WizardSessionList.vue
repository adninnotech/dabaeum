<template>
  <!-- 회차 목록 — 데스크톱은 표, 폰은 회차마다 카드(940px 표가 페이지 전체를 992px 로 밀었다) -->
  <div class="session-list">
    <div v-if="!sessions.length" class="session-list__empty">
      {{ online ? '회차가 없습니다. 회차를 추가하세요.' : '회차가 없습니다. 위에서 일정을 만들거나 회차를 추가하세요.' }}
    </div>

    <template v-else-if="$q.screen.xs">
      <div v-for="(s, i) in sessions" :key="i" class="session-card">
        <div class="session-card__head">
          <span class="session-card__no">{{ s.sessionNo }}회차</span>
          <div class="session-card__tools">
            <q-btn
              flat
              round
              dense
              size="sm"
              icon="keyboard_arrow_up"
              :aria-label="`${s.sessionNo}회차 위로`"
              :disable="i === 0"
              @click="wizard.moveSessionBy(i, -1)"
            />
            <q-btn
              flat
              round
              dense
              size="sm"
              icon="keyboard_arrow_down"
              :aria-label="`${s.sessionNo}회차 아래로`"
              :disable="i === sessions.length - 1"
              @click="wizard.moveSessionBy(i, 1)"
            />
            <q-btn
              flat
              round
              dense
              size="sm"
              icon="delete"
              class="btn-delete-session"
              :aria-label="`${s.sessionNo}회차 삭제`"
              @click="wizard.removeSession(i)"
            />
          </div>
        </div>
        <template v-if="!online">
          <div v-if="!showInputs(s)" class="session-when">
            <span class="session-when__text">{{ whenText(s) }}</span>
            <q-btn flat dense no-caps color="primary" label="고치기" @click="startEdit(s)" />
          </div>
          <template v-else>
            <AppDateInput v-model="s.date" outlined dense label="강의 날짜" />
            <div class="time-row">
              <AppTimeInput v-model="s.startTime" outlined dense class="wz-time" />
              <span class="time-row__tilde">~</span>
              <AppTimeInput v-model="s.endTime" outlined dense class="wz-time" />
            </div>
          </template>
        </template>
        <q-input v-model="s.topic" outlined dense label="주제" />
      </div>
    </template>

    <div v-else class="table-wrap">
      <!-- 시간 칸은 시각 입력 두 개(128px씩)+물결이 들어간다 — 220px 이면 끝 시각이 주제 칸을 덮었다 -->
      <table class="session-table" :class="{ 'session-table--offline': !online }">
        <colgroup>
          <col style="width: 96px" /><!-- 손잡이 + 위/아래 버튼 -->
          <col style="width: 80px" />
          <template v-if="!online">
            <col style="width: 180px" />
            <col style="width: 320px" />
          </template>
          <col />
          <col style="width: 80px" />
        </colgroup>
        <thead>
          <tr>
            <th class="col-handle" />
            <th>회차</th>
            <template v-if="!online">
              <th>강의 날짜</th>
              <th>강의 시간</th>
            </template>
            <th class="th-left">주제</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(s, i) in sessions"
            :key="i"
            draggable="true"
            @dragstart="wizard.onSessionDragStart(i)"
            @dragover.prevent
            @drop="wizard.moveSession(i)"
          >
            <td class="col-handle">
              <!-- 드래그는 마우스 전용이라 터치·키보드용 위/아래 버튼을 함께 둔다 -->
              <div class="session-order">
                <q-icon name="drag_indicator" size="20px" class="drag-handle" />
                <q-btn
                  flat
                  round
                  dense
                  size="sm"
                  icon="keyboard_arrow_up"
                  :aria-label="`${s.sessionNo}회차 위로`"
                  :disable="i === 0"
                  @click="wizard.moveSessionBy(i, -1)"
                />
                <q-btn
                  flat
                  round
                  dense
                  size="sm"
                  icon="keyboard_arrow_down"
                  :aria-label="`${s.sessionNo}회차 아래로`"
                  :disable="i === sessions.length - 1"
                  @click="wizard.moveSessionBy(i, 1)"
                />
              </div>
            </td>
            <td class="col-no">{{ s.sessionNo }}회차</td>
            <template v-if="!online">
              <!-- 일정대로 만든 회차는 글자로 — 줄마다 날짜·시간 입력칸이 펼쳐져 8회차면 표가 900px 이었다.
                   바꿀 회차만 [고치기]로 연다(직접 입력 방식·빈 칸이 있는 회차는 늘 입력칸) -->
              <template v-if="showInputs(s)">
                <td class="col-date">
                  <AppDateInput v-model="s.date" outlined dense />
                </td>
                <td class="col-time">
                  <div class="time-row">
                    <AppTimeInput v-model="s.startTime" outlined dense class="wz-time" />
                    <span class="time-row__tilde">~</span>
                    <AppTimeInput v-model="s.endTime" outlined dense class="wz-time" />
                  </div>
                </td>
              </template>
              <template v-else>
                <td class="col-date session-when__text">{{ dateText(s) }}</td>
                <td class="col-time">
                  <div class="session-when">
                    <span class="session-when__text">{{ s.startTime }}~{{ s.endTime }}</span>
                    <q-btn
                      flat
                      dense
                      no-caps
                      color="primary"
                      label="고치기"
                      :aria-label="`${s.sessionNo}회차 날짜·시간 고치기`"
                      @click="startEdit(s)"
                    />
                  </div>
                </td>
              </template>
            </template>
            <td class="col-topic">
              <q-input v-model="s.topic" outlined dense placeholder="주제를 입력해 주세요" />
            </td>
            <td class="col-manage">
              <q-btn
                flat
                round
                dense
                icon="delete"
                size="sm"
                class="btn-delete-session"
                :aria-label="`${s.sessionNo}회차 삭제`"
                @click="wizard.removeSession(i)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import AppTimeInput from '@/components/AppTimeInput.vue';

const WEEKDAY_TEXT = ['일', '월', '화', '수', '목', '금', '토'];

export default {
  name: 'WizardSessionList',
  components: { AppDateInput, AppTimeInput },
  inject: ['wizard'],
  props: {
    /** 온라인은 날짜·시간 없이 주제만 */
    online: { type: Boolean, default: false },
  },
  computed: {
    sessions() {
      return this.wizard.form.sessions;
    },
  },
  methods: {
    /** 입력칸을 펼칠 회차 — 직접 입력 방식, [고치기]를 누른 회차, 날짜·시간이 빈 회차 */
    showInputs(session) {
      if (this.wizard.form.scheduleMode === 'MANUAL' || session.editing) return true;
      return !session.date || !session.startTime || !session.endTime;
    },
    startEdit(session) {
      // 회차 객체에 표시만 단다 — 저장 본문은 필요한 칸만 골라 보내므로 서버로 가지 않는다
      session.editing = true;
    },
    dateText(session) {
      const date = new Date(`${session.date}T00:00:00`);
      if (Number.isNaN(date.getTime())) return session.date;
      return `${date.getMonth() + 1}. ${date.getDate()}. (${WEEKDAY_TEXT[date.getDay()]})`;
    },
    whenText(session) {
      return `${this.dateText(session)} ${session.startTime}~${session.endTime}`;
    },
  },
};
</script>
