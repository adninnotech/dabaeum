/**
 * 백엔드 Java 의 FabricGatewayException 코드와 같은 이름을 쓴다.
 * 모듈이 REST 로 바뀌어도 백엔드가 이미 아는 코드로 오류를 분류할 수 있게 한다.
 */
export type FabricErrorCode =
  | 'FABRIC_GATEWAY_CONNECTION_FAILED'
  | 'FABRIC_WRITE_NOT_APPROVED'
  | 'FABRIC_READ_FAILED'
  | 'FABRIC_SUBMIT_FAILED'
  | 'FABRIC_COMMIT_TIMEOUT'
  | 'FABRIC_COMMIT_INVALID';

export class FabricGatewayError extends Error {
  override readonly name = 'FabricGatewayError';

  constructor(
    readonly code: FabricErrorCode,
    message?: string,
    options?: { cause?: unknown },
  ) {
    super(message ?? code, options);
  }
}

/** peer 별 보증 실패 사유. gRPC 상태 메시지만으로는 어느 peer 가 왜 거부했는지 알 수 없다. */
export interface PeerErrorDetail {
  readonly address: string;
  readonly mspId: string;
  readonly message: string;
}
