import crypto from 'node:crypto';

/**
 * 대구체인(Mitum) 응답을 흉내 내는 데 필요한 값 생성기.
 *
 * 실제 체인의 hash·fact_hash·주소·서명은 Mitum 이 만들지만, 에뮬레이터는 Fabric 트랜잭션 ID 에서
 * 결정적으로 파생한다. 같은 Fabric 트랜잭션은 언제 조회해도 같은 fact_hash 를 준다.
 * 값의 형식(base58 길이, 0x…fca 주소, …fpu 공개키)만 맞추며 암호학적 의미는 없다.
 */

const BASE58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

export function base58(bytes: Uint8Array): string {
  let zeros = 0;
  while (zeros < bytes.length && bytes[zeros] === 0) zeros += 1;
  let n = bytes.length === 0 ? 0n : BigInt('0x' + Buffer.from(bytes).toString('hex'));
  let out = '';
  while (n > 0n) {
    const remainder = Number(n % 58n);
    out = BASE58_ALPHABET[remainder] + out;
    n /= 58n;
  }
  return '1'.repeat(zeros) + out;
}

function sha256(...parts: string[]): Buffer {
  const hash = crypto.createHash('sha256');
  for (const part of parts) hash.update(part);
  return hash.digest();
}

/** Mitum operation hash 와 fact hash. Fabric 트랜잭션 ID 에서 파생한다. */
export function operationHashes(fabricTransactionId: string): { hash: string; fact_hash: string } {
  return {
    hash: base58(sha256('operation|', fabricTransactionId)),
    fact_hash: base58(sha256('fact|', fabricTransactionId)),
  };
}

/** `0x` + 40 hex + `fca` — 대구체인 주소 형식 */
export function address(seed: string): string {
  return '0x' + sha256('address|', seed).toString('hex').slice(0, 40) + 'fca';
}

/** `02` + 64 hex + `fpu` — Mitum 공개키 표기 */
export function publicKey(seed: string): string {
  return '02' + sha256('publickey|', seed).toString('hex') + 'fpu';
}

/** 64 hex + `fpr` — Mitum 개인키 표기. 에뮬레이터 값이며 실제 키가 아니다. */
export function privateKey(seed: string): string {
  return sha256('privatekey|', seed).toString('hex') + 'fpr';
}

export function signature(seed: string): string {
  return base58(Buffer.concat([sha256('signature-a|', seed), sha256('signature-b|', seed)]));
}

/** 응답 봉투의 cid. 요청마다 다른 64 hex 식별자다. */
export function cid(): string {
  return crypto.randomBytes(32).toString('hex');
}

/** Mitum fact.token — 발행 시각을 "YYYY-MM-DD HH:mm:ss.SSS +0000 UTC" 로 적고 base64 한 값 */
export function factToken(issuedAt: Date): string {
  const iso = issuedAt.toISOString(); // 2024-09-10T02:10:04.517Z
  const text = `${iso.slice(0, 10)} ${iso.slice(11, 23)} +0000 UTC`;
  return Buffer.from(text, 'utf8').toString('base64');
}

/** 20자 대문자 — 문서 예제 UWYXTLPOYFJDPKMUNXJZ 와 같은 형식 */
export function projectId(): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  return Array.from(crypto.randomBytes(20), (byte) => alphabet[byte % alphabet.length]).join('');
}
