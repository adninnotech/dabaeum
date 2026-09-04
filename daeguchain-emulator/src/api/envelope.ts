import { cid } from '../emulator/mitum.js';

/**
 * 대구체인 응답 봉투. 문서의 모든 응답이 { state, rcode, msg, data, cid } 다.
 * 오류 형식은 문서에 정의돼 있지 않아 state="ERROR", rcode={ code, http } 로 정했다.
 */
export interface Envelope<T> {
  state: 'OK' | 'ERROR';
  rcode: Record<string, unknown>;
  msg: string;
  data: T;
  cid: string;
}

export function ok<T>(data: T): Envelope<T> {
  return { state: 'OK', rcode: {}, msg: '', data, cid: cid() };
}

export type EmulatorErrorCode =
  | 'UNAUTHORIZED'
  | 'INVALID_CHAIN'
  | 'VALIDATION_FAILED'
  | 'PROJECT_NOT_FOUND'
  | 'DATA_NOT_FOUND'
  | 'DATA_ALREADY_EXISTS'
  | 'WRITE_NOT_APPROVED'
  | 'LEDGER_READ_FAILED'
  | 'LEDGER_SUBMIT_FAILED'
  | 'LEDGER_COMMIT_TIMEOUT'
  | 'LEDGER_UNAVAILABLE'
  | 'INTERNAL_ERROR';

export class EmulatorError extends Error {
  override readonly name = 'EmulatorError';

  /**
   * @param http 실제 응답에 쓸 HTTP 상태. 업무 오류는 200 에 state=ERROR 로, 인증·연결 문제만 4xx/5xx 로 보낸다.
   */
  constructor(
    readonly code: EmulatorErrorCode,
    message: string,
    readonly http: number = 200,
  ) {
    super(message);
  }

  envelope(): Envelope<null> {
    return { state: 'ERROR', rcode: { code: this.code, http: this.http }, msg: this.message, data: null, cid: cid() };
  }
}
