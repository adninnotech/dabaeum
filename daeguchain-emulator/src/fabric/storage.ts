import { CommitStatusError, GatewayError } from '@hyperledger/fabric-gateway';
import { common, peer } from '@hyperledger/fabric-protos';
import type { Logger } from '../logger.js';
import { FabricGatewayError, type PeerErrorDetail } from './errors.js';
import type { FabricConnection } from './gateway.js';

/** 백엔드 Java 의 FabricSubmitResult 와 같은 필드. 워커가 confirmed 로 성공을 판정한다. */
export interface SubmitResult {
  readonly transactionId: string;
  readonly confirmed: boolean;
  /** Fabric TxValidationCode 이름. VALID, MVCC_READ_CONFLICT 등 */
  readonly resultCode: string;
  readonly blockNumber: bigint;
  readonly payload: Uint8Array;
}

/** Fabric SDK 타입을 밖으로 내지 않는 저수준 원장 호출 경계. Java 의 FabricStorageGateway 와 같다. */
export interface StorageGateway {
  evaluate(transactionName: string, args: readonly string[]): Promise<Uint8Array>;
  submit(transactionName: string, args: readonly string[]): Promise<SubmitResult>;
  /** 채널의 현재 블록 높이. 조회가 불가능하면 undefined. 모니터링 표시용이다. */
  chainHeight(): Promise<bigint | undefined>;
  /** 트랜잭션이 담긴 블록 번호. 대구체인 응답의 operation.height 에 쓴다. 모르면 undefined. */
  blockNumber(transactionId: string): Promise<bigint | undefined>;
}

/** Fabric TxValidationCode 번호 → 이름. VALID, MVCC_READ_CONFLICT 등 백엔드가 그대로 저장한다. */
const TX_VALIDATION_CODE_NAMES = new Map<number, string>(
  Object.entries(peer.TxValidationCode).map(([name, code]) => [code as number, name]),
);

export interface StorageGatewayOptions {
  readonly writeEnabled: boolean;
  readonly channelName: string;
}

export class FabricStorageGateway implements StorageGateway {
  /** 블록 번호는 바뀌지 않으므로 한 번 조회한 값은 기억한다. */
  private readonly blockNumbers = new Map<string, bigint>();

  constructor(
    private readonly connection: FabricConnection,
    private readonly options: StorageGatewayOptions,
    private readonly log: Logger,
  ) {}

  async evaluate(transactionName: string, args: readonly string[]): Promise<Uint8Array> {
    try {
      return await this.connection.contract.evaluateTransaction(transactionName, ...args);
    } catch (error) {
      this.log.warn({ fn: transactionName, err: error, peers: peerDetails(error) }, 'Fabric evaluate 실패');
      throw new FabricGatewayError('FABRIC_READ_FAILED', chaincodeMessage(error), { cause: error });
    }
  }

  async submit(transactionName: string, args: readonly string[]): Promise<SubmitResult> {
    if (!this.options.writeEnabled) {
      throw new FabricGatewayError('FABRIC_WRITE_NOT_APPROVED');
    }
    try {
      const proposal = this.connection.contract.newProposal(transactionName, { arguments: [...args] });
      const transaction = await proposal.endorse();
      const transactionId = transaction.getTransactionId();
      const payload = transaction.getResult();
      const commit = await transaction.submit();
      const status = await commit.getStatus();
      return {
        transactionId,
        confirmed: status.successful,
        resultCode: TX_VALIDATION_CODE_NAMES.get(status.code) ?? String(status.code),
        blockNumber: status.blockNumber,
        payload,
      };
    } catch (error) {
      if (error instanceof CommitStatusError) {
        this.log.warn({ fn: transactionName, err: error, peers: peerDetails(error) }, 'Fabric commit 확인 시간초과');
        throw new FabricGatewayError('FABRIC_COMMIT_TIMEOUT', undefined, { cause: error });
      }
      if (error instanceof GatewayError) {
        // EndorseError·SubmitError 모두 GatewayError 다. peer 별 사유는 details 에 있다.
        this.log.warn({ fn: transactionName, err: error, peers: peerDetails(error) }, 'Fabric 보증·제출 실패');
        throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', chaincodeMessage(error), { cause: error });
      }
      this.log.warn({ fn: transactionName, err: error }, 'Fabric submit 실패');
      throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', undefined, { cause: error });
    }
  }

  async chainHeight(): Promise<bigint | undefined> {
    try {
      const info = await this.connection.qscc.evaluateTransaction('GetChainInfo', this.options.channelName);
      return BigInt(common.BlockchainInfo.deserializeBinary(info).getHeight());
    } catch (error) {
      this.log.warn({ channel: this.options.channelName, err: error }, 'Fabric 블록 높이 조회 실패');
      return undefined;
    }
  }

  async blockNumber(transactionId: string): Promise<bigint | undefined> {
    const cached = this.blockNumbers.get(transactionId);
    if (cached !== undefined) return cached;
    try {
      const block = await this.connection.qscc.evaluateTransaction('GetBlockByTxID', this.options.channelName, transactionId);
      const number = common.Block.deserializeBinary(block).getHeader()?.getNumber();
      if (number === undefined) return undefined;
      const value = BigInt(number);
      if (this.blockNumbers.size > 5_000) this.blockNumbers.clear();
      this.blockNumbers.set(transactionId, value);
      return value;
    } catch (error) {
      this.log.warn({ transactionId, err: error }, 'Fabric 블록 번호 조회 실패');
      return undefined;
    }
  }
}

/**
 * peer 별 보증 실패 사유. gRPC 상태 메시지는 "see attached details" 로 끝나고
 * 실제 사유는 details 에 있어서 이것을 꺼내지 않으면 어느 peer 가 왜 거부했는지 알 수 없다.
 */
export function peerDetails(error: unknown): PeerErrorDetail[] {
  if (!(error instanceof GatewayError)) return [];
  return error.details.map((detail) => ({
    address: detail.address,
    mspId: detail.mspId,
    message: detail.message,
  }));
}

/**
 * 체인코드가 반환한 오류 문장. "storage data X already exists" 처럼 업무적으로 의미 있는
 * 메시지만 골라 API 오류 코드로 바꾸는 데 쓴다.
 */
export function chaincodeMessage(error: unknown): string | undefined {
  const details = peerDetails(error);
  const first = details.find((detail) => detail.message.trim().length > 0);
  if (first) return first.message;
  return error instanceof Error ? error.message : undefined;
}
