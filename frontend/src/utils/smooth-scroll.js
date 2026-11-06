/*
 * 창 세로 스크롤 애니메이션 — scrollIntoView({ behavior: 'smooth' })는 브라우저·OS 설정(동작 줄이기 등)에 따라
 * 바로 뛰어 '너무 바로 간다'는 말이 나왔다. 거리에 맞춘 시간으로 직접 움직인다.
 */

const MIN_MS = 350;
const MAX_MS = 700;
// 1,000px 당 늘어나는 시간
const MS_PER_1000PX = 400;

function easeInOutCubic(t) {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

export function scrollDuration(distance) {
  return Math.min(MAX_MS, Math.max(MIN_MS, Math.round((Math.abs(distance) / 1000) * MS_PER_1000PX + MIN_MS / 2)));
}

/**
 * 요소의 scroll-margin-top 을 화면 px 로. '글자 크게'(본문 zoom)를 켜면 CSS 값보다 실제로 크게 그려진다
 * — 그대로 쓰면 섹션 머리가 붙박이 탭 밑으로 들어간다
 */
export function scrollMarginOf(element) {
  const margin = Number.parseFloat(getComputedStyle(element).scrollMarginTop) || 0;
  return margin * (element.currentCSSZoom || 1);
}

/** 요소를 창 위에서 offset 만큼 아래에 맞출 스크롤 위치(문서 끝을 넘지 않게) */
export function scrollTargetOf(element, offset = 0) {
  const max = document.documentElement.scrollHeight - window.innerHeight;
  const target = element.getBoundingClientRect().top + window.scrollY - offset;
  return Math.max(0, Math.min(Math.round(target), max));
}

/**
 * targetY 까지 움직인다. { done: Promise<boolean>, cancel() } — done 은 끝까지 갔으면 true, 취소면 false.
 * 탭이 백그라운드라 프레임이 안 돌면 바로 옮긴다
 */
export function animateWindowScroll(targetY) {
  const startY = window.scrollY;
  const distance = targetY - startY;
  if (!distance || document.hidden) {
    window.scrollTo(0, targetY);
    return { done: Promise.resolve(true), cancel() {} };
  }
  const duration = scrollDuration(distance);
  let frame = 0;
  let finish = () => {};
  const done = new Promise((resolve) => {
    finish = resolve;
  });
  const startedAt = performance.now();
  const step = (now) => {
    const progress = Math.min(1, (now - startedAt) / duration);
    window.scrollTo(0, startY + distance * easeInOutCubic(progress));
    if (progress < 1) frame = window.requestAnimationFrame(step);
    else finish(true);
  };
  frame = window.requestAnimationFrame(step);
  return {
    done,
    cancel() {
      window.cancelAnimationFrame(frame);
      finish(false);
    },
  };
}
