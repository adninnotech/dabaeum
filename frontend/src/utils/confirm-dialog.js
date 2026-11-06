/**
 * 되돌리기 어려운 동작 앞 확인 창 — 확인이면 true, 취소·바깥 클릭이면 false.
 * 사유가 필요한 동작은 이 대신 components/console/CorrectionReasonDialog 를 쓴다.
 */
/* html: true 면 message 를 HTML 로 그린다 — 넣는 값은 부르는 쪽이 이스케이프한다 */
export function confirmDialog($q, { title, message, html = false, ok = '확인', color = 'primary', cancel = '취소' }) {
  return new Promise((resolve) => {
    $q.dialog({
      title,
      message,
      html,
      cancel: { flat: true, noCaps: true, label: cancel },
      ok: { unelevated: true, noCaps: true, color, label: ok },
    })
      .onOk(() => resolve(true))
      .onCancel(() => resolve(false));
  });
}
