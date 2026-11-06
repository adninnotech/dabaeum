/**
 * 프로필 사진 원형 자르기 — 화면(무대) 좌표와 원본 사진 좌표를 잇는 계산만 모았다(그리기·이벤트 없음).
 * 무대 가운데에 지름 `circle` 의 원이 있고, 사진은 무대 가운데 + offset 에 scale 배로 놓인다.
 */
export const AVATAR_OUTPUT_SIZE = 512;
export const AVATAR_MAX_ZOOM = 4;

/** 원을 빈틈없이 덮는 가장 작은 배율 — 길쭉한 사진도 짧은 변이 원 지름에 맞는다 */
export function coverScale(image, circle) {
  return Math.max(circle / image.width, circle / image.height);
}

/** 사진이 원 밖으로 빠져 빈 곳이 생기지 않게 offset 을 가둔다 */
export function clampOffset(offset, image, scale, circle) {
  const maxX = Math.max(0, (image.width * scale - circle) / 2);
  const maxY = Math.max(0, (image.height * scale - circle) / 2);
  return {
    x: Math.min(maxX, Math.max(-maxX, offset.x)),
    y: Math.min(maxY, Math.max(-maxY, offset.y)),
  };
}

/** 원이 덮는 원본 사진의 정사각형 영역 { sx, sy, size } */
export function sourceSquare(image, scale, offset, circle) {
  const size = circle / scale;
  return {
    sx: image.width / 2 - (circle / 2 + offset.x) / scale,
    sy: image.height / 2 - (circle / 2 + offset.y) / scale,
    size,
  };
}

/**
 * 원 안만 남긴 PNG(원 밖은 투명)
 * @param {HTMLImageElement} image
 * @param {{ sx: number, sy: number, size: number }} square
 * @returns {Promise<Blob>}
 */
export function renderCircleAvatar(image, square, outputSize = AVATAR_OUTPUT_SIZE) {
  const canvas = document.createElement('canvas');
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext('2d');
  ctx.beginPath();
  ctx.arc(outputSize / 2, outputSize / 2, outputSize / 2, 0, Math.PI * 2);
  ctx.clip();
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(image, square.sx, square.sy, square.size, square.size, 0, 0, outputSize, outputSize);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('사진을 자르지 못했습니다.'))), 'image/png');
  });
}
