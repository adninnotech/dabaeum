import { Notify } from 'quasar';

export async function copyToClipboard(text, { successMessage = '클립보드에 복사했습니다.' } = {}) {
  const value = String(text || '');
  if (!value) return false;

  try {
    await navigator.clipboard.writeText(value);
    Notify.create({ type: 'positive', message: successMessage, position: 'top' });
    return true;
  } catch {
    Notify.create({ type: 'negative', message: '복사에 실패했습니다.', position: 'top' });
    return false;
  }
}
