/* 여러 수강생에게 같은 요청을 보낼 때 쓰는 작은 동시 실행기.
 * 30명을 한꺼번에 보내면 서버·브라우저 연결이 몰리고, 하나씩 보내면 느리다 — 몇 개씩만 동시에 보낸다.
 * 하나가 실패해도 나머지는 계속한다(일괄 처리 끝에 성공·실패를 한 번에 알리기 위해). */

export const DEFAULT_CONCURRENCY = 3;

/**
 * @param {Array} items 처리할 항목
 * @param {(item) => Promise<any>} worker 항목 하나를 처리
 * @param {{ concurrency?: number, onProgress?: (done: number, total: number) => void }} options
 * @returns {Promise<Array<{ item, ok: boolean, value?, error? }>>} items 와 같은 순서
 */
export async function runPool(
  items,
  worker,
  { concurrency = DEFAULT_CONCURRENCY, onProgress } = {},
) {
  const list = Array.isArray(items) ? items : [];
  const results = new Array(list.length);
  let next = 0;
  let done = 0;

  const runNext = async () => {
    while (next < list.length) {
      const index = next;
      next += 1;
      const item = list[index];
      try {
        results[index] = { item, ok: true, value: await worker(item) };
      } catch (error) {
        results[index] = { item, ok: false, error };
      }
      done += 1;
      if (onProgress) onProgress(done, list.length);
    }
  };

  const lanes = Math.max(1, Math.min(concurrency, list.length));
  await Promise.all(Array.from({ length: lanes }, runNext));
  return results;
}
