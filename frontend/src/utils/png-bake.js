/**
 * PNG 안에 글(서명된 수료증 JWT 등)을 숨겨 넣고 꺼낸다 — iTXt 청크(PNG 1.2 §11.3.4.5).
 * 그림은 그대로이고, 검증 도구가 키워드로 청크를 찾아 원문을 읽는다.
 */
const PNG_SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];
const IEND = 'IEND';

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i += 1) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function concat(parts) {
  const out = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
  parts.reduce((offset, part) => {
    out.set(part, offset);
    return offset + part.length;
  }, 0);
  return out;
}

function uint32(value) {
  return new Uint8Array([(value >>> 24) & 0xff, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff]);
}

function readUint32(bytes, offset) {
  return ((bytes[offset] << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3]) >>> 0;
}

function isPng(bytes) {
  return PNG_SIGNATURE.every((value, index) => bytes[index] === value);
}

/** iTXt 데이터: 키워드 \0 압축안함(0) 방식(0) 언어 \0 번역키워드 \0 본문(UTF-8) */
function itxtChunk(keyword, text) {
  const encoder = new TextEncoder();
  const type = encoder.encode('iTXt');
  const data = concat([encoder.encode(keyword), new Uint8Array([0, 0, 0, 0, 0]), encoder.encode(text)]);
  const body = concat([type, data]);
  return concat([uint32(data.length), body, uint32(crc32(body))]);
}

/** 청크 목록 [{ type, start, dataStart, length }] — 망가진 파일이면 던진다 */
function listChunks(bytes) {
  if (!isPng(bytes)) throw new Error('PNG 파일이 아닙니다.');
  const chunks = [];
  let offset = PNG_SIGNATURE.length;
  while (offset + 8 <= bytes.length) {
    const length = readUint32(bytes, offset);
    const type = String.fromCharCode(...bytes.slice(offset + 4, offset + 8));
    chunks.push({ type, start: offset, dataStart: offset + 8, length });
    offset += 12 + length;
    if (type === IEND) break;
  }
  return chunks;
}

/** PNG 바이트에 iTXt 를 IEND 바로 앞에 넣은 새 바이트 */
export function bakePngBytes(bytes, keyword, text) {
  const end = listChunks(bytes).find((chunk) => chunk.type === IEND);
  if (!end) throw new Error('PNG 끝(IEND)을 찾지 못했습니다.');
  return concat([bytes.slice(0, end.start), itxtChunk(keyword, text), bytes.slice(end.start)]);
}

export async function bakePngBlob(blob, keyword, text) {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  return new Blob([bakePngBytes(bytes, keyword, text)], { type: 'image/png' });
}

/** 숨겨 넣은 글 꺼내기 — 없으면 null */
export function readBakedText(bytes, keyword) {
  const decoder = new TextDecoder();
  const found = listChunks(bytes)
    .filter((chunk) => chunk.type === 'iTXt')
    .map((chunk) => bytes.slice(chunk.dataStart, chunk.dataStart + chunk.length))
    .find((data) => decoder.decode(data.slice(0, data.indexOf(0))) === keyword);
  if (!found) return null;
  // 키워드 \0 압축 방식 언어 \0 번역키워드 \0 본문
  let offset = found.indexOf(0) + 3;
  offset = found.indexOf(0, offset) + 1;
  offset = found.indexOf(0, offset) + 1;
  return decoder.decode(found.slice(offset));
}
