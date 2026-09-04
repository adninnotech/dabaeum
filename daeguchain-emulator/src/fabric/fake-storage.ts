import type { StorageGateway, SubmitResult } from './storage.js';
import { FabricGatewayError } from './errors.js';

/**
 * Fabric 없이 모듈 API 를 돌려보기 위한 메모리 원장.
 * 체인코드 continualtion-edu 의 CreateData/GetData/UpdateData/DeleteData/GetDataHistory 를
 * 같은 규칙(키 형식, 중복 생성 거부, 미존재 조회 오류)으로 흉내 낸다.
 */
export class InMemoryStorageGateway implements StorageGateway {
  private readonly state = new Map<string, string>();
  private readonly history = new Map<string, Array<{ txId: string; timestamp: string; isDelete: boolean; value?: unknown }>>();
  private height = 1n;
  private sequence = 0;
  private readonly heights = new Map<string, bigint>();

  constructor(private readonly writeEnabled: boolean) {}

  async evaluate(transactionName: string, args: readonly string[]): Promise<Uint8Array> {
    const encode = (value: unknown) => new TextEncoder().encode(JSON.stringify(value));
    switch (transactionName) {
      case 'GetData': {
        const key = args[0] ?? '';
        const value = this.state.get(key);
        if (value === undefined) throw new FabricGatewayError('FABRIC_READ_FAILED', `storage data ${key} does not exist`);
        return encode({ dataKey: key, dataValue: value });
      }
      case 'GetDataHistory':
        // Fabric 의 GetHistoryForKey 와 같게 최신순으로 돌려준다. 호출자가 순서에 기대면 여기서 드러난다.
        return encode([...(this.history.get(args[0] ?? '') ?? [])].reverse());
      default:
        throw new FabricGatewayError('FABRIC_READ_FAILED', `unknown function ${transactionName}`);
    }
  }

  async submit(transactionName: string, args: readonly string[]): Promise<SubmitResult> {
    if (!this.writeEnabled) throw new FabricGatewayError('FABRIC_WRITE_NOT_APPROVED');
    const key = args[0] ?? '';
    const value = args[1] ?? '';
    switch (transactionName) {
      case 'CreateData':
        if (this.state.has(key)) throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', `storage data ${key} already exists`);
        this.state.set(key, value);
        return this.record(key, false, { dataKey: key, dataValue: value });
      case 'UpdateData':
        if (!this.state.has(key)) throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', `storage data ${key} does not exist`);
        this.state.set(key, value);
        return this.record(key, false, { dataKey: key, dataValue: value });
      case 'DeleteData':
        if (!this.state.has(key)) throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', `storage data ${key} does not exist`);
        this.state.delete(key);
        return this.record(key, true);
      default:
        throw new FabricGatewayError('FABRIC_SUBMIT_FAILED', `unknown function ${transactionName}`);
    }
  }

  async chainHeight(): Promise<bigint | undefined> {
    return this.height;
  }

  async blockNumber(transactionId: string): Promise<bigint | undefined> {
    return this.heights.get(transactionId);
  }

  private record(key: string, isDelete: boolean, value?: unknown): SubmitResult {
    this.sequence += 1;
    const txId = `fake-${String(this.sequence).padStart(6, '0')}`;
    const entry = { txId, timestamp: new Date().toISOString(), isDelete, ...(value === undefined ? {} : { value }) };
    this.history.set(key, [...(this.history.get(key) ?? []), entry]);
    // Fabric 과 같은 의미: 블록 번호는 0 기반이고 chainHeight 는 블록 수다. 최신 트랜잭션의 블록 번호는 높이 - 1.
    const blockNumber = this.height;
    this.height += 1n;
    this.heights.set(txId, blockNumber);
    return { transactionId: txId, confirmed: true, resultCode: 'VALID', blockNumber, payload: new Uint8Array() };
  }
}
