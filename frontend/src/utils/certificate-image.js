/**
 * 수료증 이미지(PNG) — A4 세로(150dpi)로 그리고, 서명된 수료증(VC-JWT)을 파일 안에 숨겨 넣는다.
 * 그림엔 QR 을 넣지 않는다 — 검증 도구가 파일 속 JWT 서명을 확인한다(사람용 진위 확인은 수료증 상세의 링크).
 */
import { CERTIFICATE_OPERATOR, CERTIFICATE_PNG_KEYWORD } from '@/config/certificate';
import { drawCertificateFrame } from '@/utils/certificate-frame';
import { bakePngBlob } from '@/utils/png-bake';

const WIDTH = 1240;
const HEIGHT = 1754;
const FONT = "'Pretendard Variable', 'Pretendard', 'Noto Sans KR', 'Apple SD Gothic Neo', sans-serif";
// 캔버스는 CSS 변수를 못 읽는다 — 흰 무지 종이(틀은 금박, certificate-frame), 글자는 인쇄해도 또렷한 먹색
const COLOR = { ink: '#1b1f27', sub: '#555b66', paper: '#ffffff' };
const LABEL_X = 220;
const VALUE_X = 470;
const VALUE_MAX = WIDTH - VALUE_X - 200;

function font(size, weight = 400) {
  return `${weight} ${size}px ${FONT}`;
}

/** 낱말 단위로 maxWidth 안에 줄을 나눈다. 한 낱말이 한 줄보다 길면 그 낱말만 글자 단위로 자른다 */
function wrapLines(ctx, text, maxWidth) {
  const fits = (value) => ctx.measureText(value).width <= maxWidth;
  const splitLongWord = (word) =>
    Array.from(word).reduce(
      (parts, char) => {
        const last = parts[parts.length - 1];
        return fits(last + char) || !last ? [...parts.slice(0, -1), last + char] : [...parts, char];
      },
      [''],
    );
  return String(text || '')
    .split(/\s+/)
    .filter(Boolean)
    .flatMap((word) => (fits(word) ? [word] : splitLongWord(word)))
    .reduce(
      (lines, word) => {
        const last = lines[lines.length - 1];
        const joined = last ? `${last} ${word}` : word;
        return fits(joined) || !last ? [...lines.slice(0, -1), joined] : [...lines, word];
      },
      [''],
    );
}

function drawHeader(ctx, credentialNo) {
  // 모서리 장식(위 왼쪽)을 피해 제목 위 가운데에
  ctx.textAlign = 'center';
  ctx.fillStyle = COLOR.sub;
  ctx.font = font(24);
  ctx.fillText(`증서번호 ${credentialNo || '-'}`, WIDTH / 2, 230);
  ctx.textAlign = 'center';
  ctx.fillStyle = COLOR.ink;
  ctx.font = font(104, 800);
  // 글자 사이를 띄워 증서 제목답게
  ctx.fillText('수   료   증', WIDTH / 2, 380);
}

/** 항목 표 — 강좌명이 길면 줄을 바꾸고 아래 항목을 내린다. 끝난 y 를 돌려준다 */
function drawFields(ctx, fields, startY) {
  return fields.reduce((y, field) => {
    ctx.textAlign = 'left';
    ctx.fillStyle = COLOR.sub;
    ctx.font = font(36, 600);
    ctx.fillText(field.label, LABEL_X, y);
    ctx.fillStyle = COLOR.ink;
    ctx.font = font(38, 700);
    const lines = wrapLines(ctx, field.value || '-', VALUE_MAX);
    lines.forEach((line, index) => ctx.fillText(line, VALUE_X, y + index * 52));
    return y + 52 * (lines.length - 1) + 88;
  }, startY);
}

function drawCentered(ctx, text, y, size, weight, color, lineHeight) {
  ctx.textAlign = 'center';
  ctx.fillStyle = color;
  ctx.font = font(size, weight);
  const lines = wrapLines(ctx, text, WIDTH - 360);
  lines.forEach((line, index) => ctx.fillText(line, WIDTH / 2, y + index * lineHeight));
  return y + lines.length * lineHeight;
}

function drawFootnote(ctx) {
  ctx.textAlign = 'center';
  ctx.fillStyle = COLOR.sub;
  ctx.font = font(20);
  // 아래 변 가운데 장식 위로 띄운다
  ctx.fillText('이 이미지 파일에는 발급기관이 서명한 전자 수료증(VC)이 들어 있습니다.', WIDTH / 2, HEIGHT - 175);
}

async function waitFonts() {
  try {
    await Promise.all([document.fonts.load(font(104, 800)), document.fonts.load(font(36, 400))]);
  } catch {
    // 글꼴을 못 불러와도 시스템 글꼴로 그린다
  }
}

function toBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('수료증 이미지를 만들지 못했습니다.'))), 'image/png');
  });
}

/**
 * @param {object} data
 * @param {string} data.credentialNo
 * @param {string} data.name 성명
 * @param {string} data.birthDate 생년월일(표시용)
 * @param {string} data.courseTitle 강좌명
 * @param {string} data.period 교육기간(표시용)
 * @param {string} data.issuedDate 일자(표시용, '2026년 10월 13일')
 * @param {string} data.institutionName 강좌를 진행한 기관
 * @param {string} data.jwt 서명된 수료증 — 파일 안에 숨겨 넣는다
 * @returns {Promise<Blob>} PNG
 */
export async function renderCertificatePng(data) {
  if (!data?.jwt) throw new Error('서명된 수료증이 없어 이미지를 만들 수 없습니다.');
  await waitFonts();
  const canvas = document.createElement('canvas');
  canvas.width = WIDTH;
  canvas.height = HEIGHT;
  const ctx = canvas.getContext('2d');
  ctx.textBaseline = 'alphabetic';

  drawCertificateFrame(ctx, WIDTH, HEIGHT, COLOR.paper);
  drawHeader(ctx, data.credentialNo);
  const fieldsEnd = drawFields(
    ctx,
    [
      { label: '성      명', value: data.name },
      { label: '생 년 월 일', value: data.birthDate },
      { label: '강   좌   명', value: data.courseTitle },
      { label: '교 육 기 간', value: data.period },
    ],
    // 평생학번 칸이 빠져(#142) 네 줄 — 본문(1080)과의 빈자리가 커지지 않게 조금 내려 시작한다
    610,
  );
  const host = data.institutionName || CERTIFICATE_OPERATOR;
  const bodyEnd = drawCentered(
    ctx,
    `위 사람은 ${host}에서 운영한 위 교육과정을 성실히 이수하였으므로 이 증서를 수여합니다.`,
    Math.max(fieldsEnd + 60, 1080),
    40,
    500,
    COLOR.ink,
    62,
  );
  const dateEnd = drawCentered(ctx, data.issuedDate || '', bodyEnd + 80, 40, 600, COLOR.ink, 60);
  const hostEnd = drawCentered(ctx, host, dateEnd + 90, 52, 800, COLOR.ink, 66);
  if (host !== CERTIFICATE_OPERATOR) drawCentered(ctx, CERTIFICATE_OPERATOR, hostEnd + 4, 30, 600, COLOR.sub, 44);
  drawFootnote(ctx);

  return bakePngBlob(await toBlob(canvas), CERTIFICATE_PNG_KEYWORD, data.jwt);
}
