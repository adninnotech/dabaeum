import { copyToClipboard as quasarCopy, Notify } from 'quasar';

// navigator.clipboard 는 https·localhost 에서만 있다 — dev 서버(http IP)에서도 되도록
// execCommand 대체 경로가 있는 Quasar copyToClipboard 를 쓴다
export async function copyToClipboard(text, { successMessage = '클립보드에 복사했습니다.' } = {}) {
  const value = String(text || '');
  if (!value) return false;

  try {
    await quasarCopy(value);
    Notify.create({ type: 'positive', message: successMessage, position: 'top' });
    return true;
  } catch {
    Notify.create({ type: 'negative', message: '복사에 실패했습니다.', position: 'top' });
    return false;
  }
}
