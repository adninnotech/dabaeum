/**
 * 수료증 금박 액자 틀 — 흰 무지 종이 위에 상하좌우 대칭으로 그린다.
 * 굵은 바깥선 + 가는 안쪽선(사이 흰 띠), 안쪽으로 오목하게 꺾인 모서리,
 * 네 모서리 덩굴·소용돌이 장식, 위·아래 변 가운데 작은 곡선 장식. 변은 깔끔한 직선.
 * 장식은 '모서리 하나의 반쪽'만 그리고 대각선·좌우·상하로 뒤집어 네 곳에 찍는다.
 */
// 메탈릭 골드 — 단색 노랑이 아니라 밝고 어두운 톤을 번갈아 둔다
const GOLD_STOPS = [
  [0, '#8a6a1f'],
  [0.18, '#e8cd78'],
  [0.34, '#a87d27'],
  [0.5, '#f7e6a6'],
  [0.66, '#9c7426'],
  [0.82, '#e3c46c'],
  [1, '#7d5e1a'],
];
const OUTER_LINE = 10;
const INNER_LINE = 3;
const LINE_GAP = 16; // 두 선 사이 흰 띠
const NOTCH = 46; // 오목 모서리 반지름

function goldGradient(ctx, x0, y0, x1, y1) {
  const gradient = ctx.createLinearGradient(x0, y0, x1, y1);
  GOLD_STOPS.forEach(([offset, color]) => gradient.addColorStop(offset, color));
  return gradient;
}

/** 모서리가 안쪽으로 오목하게 파인 사각형 — 반지름 r 의 원호 중심이 각 모서리 점 */
function concaveRectPath(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.arc(x + w, y, r, Math.PI, Math.PI / 2, true);
  ctx.lineTo(x + w, y + h - r);
  ctx.arc(x + w, y + h, r, (3 * Math.PI) / 2, Math.PI, true);
  ctx.lineTo(x + r, y + h);
  ctx.arc(x, y + h, r, 0, -Math.PI / 2, true);
  ctx.lineTo(x, y + r);
  ctx.arc(x, y, r, Math.PI / 2, 0, true);
  ctx.closePath();
}

/** 끝이 뾰족한 잎 하나 — from 에서 to 로, bulge 만큼 부푼다 */
function leaf(ctx, from, to, bulge) {
  const mx = (from[0] + to[0]) / 2;
  const my = (from[1] + to[1]) / 2;
  const nx = -(to[1] - from[1]);
  const ny = to[0] - from[0];
  const len = Math.hypot(nx, ny) || 1;
  const ox = (nx / len) * bulge;
  const oy = (ny / len) * bulge;
  ctx.beginPath();
  ctx.moveTo(from[0], from[1]);
  ctx.quadraticCurveTo(mx + ox, my + oy, to[0], to[1]);
  ctx.quadraticCurveTo(mx - ox, my - oy, from[0], from[1]);
  ctx.fill();
}

/** 모서리 장식의 반쪽(윗변을 따라 뻗는 쪽). 원점 = 안쪽선의 모서리, +x 오른쪽 · +y 아래 */
function cornerHalf(ctx) {
  ctx.lineWidth = 3;
  // 굵은 덩굴 — 오목 모서리 곁에서 나와 변을 따라 뻗다가 안쪽으로 말린다
  ctx.beginPath();
  ctx.moveTo(40, 40);
  ctx.bezierCurveTo(62, 22, 102, 18, 136, 26);
  ctx.bezierCurveTo(166, 33, 182, 52, 170, 66);
  ctx.bezierCurveTo(160, 77, 142, 70, 147, 58);
  ctx.bezierCurveTo(150, 51, 159, 52, 159, 58);
  ctx.stroke();
  // 가는 덩굴 — 더 멀리 변을 따라 가다 작게 말린다
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(136, 26);
  ctx.bezierCurveTo(176, 16, 214, 18, 246, 24);
  ctx.bezierCurveTo(262, 27, 266, 40, 255, 43);
  ctx.bezierCurveTo(248, 45, 245, 37, 251, 35);
  ctx.stroke();
  // 잎 두 장
  leaf(ctx, [84, 24], [104, 46], 7);
  leaf(ctx, [196, 19], [214, 4], 5);
}

/** 대각선 위의 가운데 소용돌이 — 반쪽 둘이 만나는 곳(대칭이라 한 번만) */
function cornerCenter(ctx) {
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.moveTo(40, 40);
  ctx.bezierCurveTo(56, 56, 76, 62, 82, 82);
  ctx.bezierCurveTo(86, 96, 72, 104, 64, 94);
  ctx.bezierCurveTo(58, 86, 66, 78, 72, 84);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(30, 30, 6, 0, Math.PI * 2);
  ctx.fill();
  leaf(ctx, [44, 44], [22, 62], 6);
  leaf(ctx, [44, 44], [62, 22], 6);
}

/** 위 변 가운데 장식의 반쪽(오른쪽). 원점 = 안쪽선 위의 가운데 점, +y 아래 */
function edgeHalf(ctx) {
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.moveTo(0, 14);
  ctx.bezierCurveTo(18, 12, 34, 2, 54, 10);
  ctx.bezierCurveTo(70, 17, 66, 34, 54, 33);
  ctx.bezierCurveTo(46, 32, 46, 23, 53, 23);
  ctx.stroke();
  leaf(ctx, [8, 16], [26, 30], 5);
}

function edgeCenter(ctx) {
  leaf(ctx, [0, 4], [0, 34], 8);
}

/** 원점·방향을 바꿔 그리기 — sx·sy 는 ±1(뒤집기), swap 은 대각선 뒤집기 */
function withTransform(ctx, { x, y, sx = 1, sy = 1, swap = false }, draw) {
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(sx, sy);
  if (swap) ctx.transform(0, 1, 1, 0, 0, 0);
  draw();
  ctx.restore();
}

/**
 * @param {CanvasRenderingContext2D} ctx
 * @param {number} width
 * @param {number} height
 * @param {string} paper 종이색
 */
export function drawCertificateFrame(ctx, width, height, paper) {
  ctx.fillStyle = paper;
  ctx.fillRect(0, 0, width, height);

  const gold = goldGradient(ctx, 0, 0, width, height);
  ctx.strokeStyle = gold;
  ctx.fillStyle = gold;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  // 금박 광택 — 아주 옅은 그림자로 선이 종이 위에 얹힌 느낌
  ctx.shadowColor = 'rgba(110, 80, 20, 0.28)';
  ctx.shadowBlur = 2;
  ctx.shadowOffsetY = 1;

  const margin = Math.round(width * 0.055);
  ctx.lineWidth = OUTER_LINE;
  concaveRectPath(ctx, margin, margin, width - margin * 2, height - margin * 2, NOTCH);
  ctx.stroke();

  const inset = margin + OUTER_LINE / 2 + LINE_GAP;
  ctx.lineWidth = INNER_LINE;
  concaveRectPath(ctx, inset, inset, width - inset * 2, height - inset * 2, NOTCH);
  ctx.stroke();

  const corners = [
    { x: inset, y: inset, sx: 1, sy: 1 },
    { x: width - inset, y: inset, sx: -1, sy: 1 },
    { x: inset, y: height - inset, sx: 1, sy: -1 },
    { x: width - inset, y: height - inset, sx: -1, sy: -1 },
  ];
  corners.forEach((corner) => {
    withTransform(ctx, corner, () => cornerHalf(ctx));
    withTransform(ctx, { ...corner, swap: true }, () => cornerHalf(ctx));
    withTransform(ctx, corner, () => cornerCenter(ctx));
  });

  [
    { x: width / 2, y: inset, sy: 1 },
    { x: width / 2, y: height - inset, sy: -1 },
  ].forEach((edge) => {
    withTransform(ctx, { ...edge, sx: 1 }, () => edgeHalf(ctx));
    withTransform(ctx, { ...edge, sx: -1 }, () => edgeHalf(ctx));
    withTransform(ctx, edge, () => edgeCenter(ctx));
  });

  ctx.shadowColor = 'transparent';
  ctx.shadowBlur = 0;
  ctx.shadowOffsetY = 0;
}
