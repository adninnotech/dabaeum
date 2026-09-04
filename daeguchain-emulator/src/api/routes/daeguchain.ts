import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import type { ServerDependencies } from '../server.js';
import type { SubmitResult } from '../../fabric/storage.js';
import { FabricGatewayError } from '../../fabric/errors.js';
import { EmulatorError, ok } from '../envelope.js';
import { address, factToken, operationHashes, privateKey, publicKey, signature } from '../../emulator/mitum.js';
import type { StorageProject } from '../../emulator/projects.js';

/* ---------- 요청 스키마 (문서의 요청 본문 그대로) ---------- */

const base = z.object({
  token: z.string().optional(),
  chain: z.string().optional(),
});

/** 최대 20자, 공백과 : / ? # [ ] @ 금지 — Create Data 문서의 data_key 규칙 */
const dataKey = z
  .string()
  .min(1)
  .max(20)
  .regex(/^[^\s:/?#[\]@]+$/, 'data_key 는 공백과 : / ? # [ ] @ 를 포함할 수 없다');
const dataValue = z.string().max(200);
const projectIdField = z.string().min(1);
const flexibleInt = z.union([z.number(), z.string()]).optional();
const flexibleBool = z.union([z.boolean(), z.string()]).optional();

const registProjectBody = base.extend({
  operation: z.union([z.literal('0'), z.literal('1'), z.literal(0), z.literal(1)]),
  project_id: z.string().optional(),
  project_name: z.string().max(10).optional(),
});
const createDataBody = base.extend({ project_id: projectIdField, data_key: dataKey, data_value: dataValue });
const updateDataBody = createDataBody;
const keyOnlyBody = base.extend({ project_id: projectIdField, data_key: dataKey });
const historyBody = keyOnlyBody.extend({ limit: flexibleInt, offset: flexibleInt, reverse: flexibleBool });

/* ---------- 응답 조립 ---------- */

type DataOperation = 'create' | 'update' | 'delete';

const FACT_HINT: Record<DataOperation, string> = {
  create: 'mitum-storage-create-data-operation-fact-v0.0.1',
  update: 'mitum-storage-update-data-operation-fact-v0.0.1',
  delete: 'mitum-storage-delete-data-operation-fact-v0.0.1',
};
const OPERATION_HINT: Record<DataOperation, string> = {
  create: 'mitum-storage-create-data-operation-v0.0.1',
  update: 'mitum-storage-update-data-operation-v0.0.1',
  delete: 'mitum-storage-delete-data-operation-v0.0.1',
};

interface FabricHistoryRecord {
  txId: string;
  timestamp: string;
  isDelete: boolean;
  value?: { dataKey: string; dataValue: string };
}

/**
 * Create/Update/Delete Data 응답의 data 부분. Fabric 영수증(트랜잭션 ID, 블록 번호, 확정 여부)을
 * Mitum operation·receipt 모양으로 옮긴다.
 */
function dataOperationResponse(
  kind: DataOperation,
  project: StorageProject,
  key: string,
  value: string | undefined,
  result: SubmitResult,
  issuedAt: Date,
  signerSeed: string,
) {
  const { hash, fact_hash } = operationHashes(result.transactionId);
  const signer = publicKey(signerSeed);
  const fact = {
    hash: fact_hash,
    token: factToken(issuedAt),
    _hint: FACT_HINT[kind],
    sender: project.owner,
    contract: project.contract,
    dataKey: key,
    ...(value === undefined ? {} : { dataValue: value }),
    currency: 'FACT',
  };
  const operation = {
    hash,
    fact,
    signs: [{ signed_at: issuedAt.toISOString(), signer, signature: signature(`${result.transactionId}|${signerSeed}`) }],
    _hint: OPERATION_HINT[kind],
  };
  return {
    contract: project.contract,
    data: { data_key: key, ...(value === undefined ? {} : { data_value: value }) },
    tx: { hash, fact_hash },
    issued: issuedAt.toISOString(),
    response: operation,
    receipt: {
      _hint: 'mitum-currency-operation-value-v0.0.1',
      hash: fact_hash,
      operation,
      height: Number(result.blockNumber),
      confirmed_at: new Date().toISOString(),
      // Fabric 이 무효 판정하면 그 코드를 reason 에 남긴다. 정상은 빈 문자열이다.
      reason: result.confirmed ? '' : result.resultCode,
      in_state: result.confirmed,
      index: 0,
    },
  };
}

/** Get Data · Data History 의 항목. */
function dataRecord(project: StorageProject, key: string, value: string, deleted: boolean, txId: string, timestamp: string, height: bigint | undefined) {
  return {
    cont_addr: project.contract,
    data_key: key,
    data_value: deleted ? '' : value,
    deleted,
    operation: {
      fact_hash: operationHashes(txId).fact_hash,
      timestamp,
      height: height === undefined ? 0 : Number(height),
    },
  };
}

function parseJson<T>(payload: Uint8Array): T {
  return JSON.parse(new TextDecoder().decode(payload)) as T;
}

/** 체인코드 오류 문장을 대구체인식 업무 오류로 옮긴다. */
function translate(error: unknown): EmulatorError {
  if (error instanceof EmulatorError) return error;
  if (error instanceof FabricGatewayError) {
    const message = error.message ?? '';
    if (/already exists/i.test(message)) return new EmulatorError('DATA_ALREADY_EXISTS', message);
    if (/does not exist|not found/i.test(message)) return new EmulatorError('DATA_NOT_FOUND', message);
    switch (error.code) {
      case 'FABRIC_WRITE_NOT_APPROVED':
        return new EmulatorError('WRITE_NOT_APPROVED', '원장 쓰기가 승인되지 않았다. FABRIC_WRITE_ENABLED 를 확인하라', 403);
      case 'FABRIC_READ_FAILED':
        return new EmulatorError('LEDGER_READ_FAILED', '원장 조회에 실패했다', 502);
      case 'FABRIC_SUBMIT_FAILED':
        return new EmulatorError('LEDGER_SUBMIT_FAILED', '원장 제출에 실패했다', 502);
      case 'FABRIC_COMMIT_TIMEOUT':
        return new EmulatorError('LEDGER_COMMIT_TIMEOUT', '원장 커밋 확인이 시간 안에 끝나지 않았다', 504);
      case 'FABRIC_COMMIT_INVALID':
        return new EmulatorError('LEDGER_SUBMIT_FAILED', '원장이 트랜잭션을 무효로 판정했다', 502);
      case 'FABRIC_GATEWAY_CONNECTION_FAILED':
        return new EmulatorError('LEDGER_UNAVAILABLE', '원장에 연결할 수 없다', 503);
    }
  }
  if (error instanceof z.ZodError) {
    const detail = error.issues.map((issue) => `${issue.path.join('.') || '(body)'}: ${issue.message}`).join('; ');
    return new EmulatorError('VALIDATION_FAILED', detail);
  }
  return new EmulatorError('INTERNAL_ERROR', '내부 오류', 500);
}

function toInt(value: number | string | undefined, fallback: number): number {
  if (value === undefined || value === null || value === '') return fallback;
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : fallback;
}

function toBool(value: boolean | string | undefined): boolean {
  if (typeof value === 'boolean') return value;
  return value === 'true' || value === '1';
}

/**
 * 대구체인 Storage API 와 같은 경로·요청·응답. 백엔드는 이 모듈을 대구체인처럼 호출하고,
 * 실제 대구체인으로 바꿀 때 호스트만 교체한다.
 *
 * 문서: 대구체인-Storage-API-Project-list.md (Project list / Regist project / Create / Delete / Get / History / Update)
 */
export function registerDaeguChainRoutes(app: FastifyInstance, deps: ServerDependencies): void {
  const { storage, projects, emulator } = deps;

  const requireProject = (id: string): StorageProject => {
    const project = projects.get(id);
    if (!project) throw new EmulatorError('PROJECT_NOT_FOUND', `project ${id} does not exist`);
    return project;
  };

  /** token·chain 검사. 문서에는 오류 형식이 없어 인증 실패만 401 로 보낸다. */
  const authenticate = (request: FastifyRequest): void => {
    const body = base.parse(request.body ?? {});
    if (emulator.token && body.token !== emulator.token) {
      throw new EmulatorError('UNAUTHORIZED', 'token 이 없거나 틀리다', 401);
    }
    if (body.chain !== undefined && body.chain !== emulator.chain) {
      throw new EmulatorError('INVALID_CHAIN', `chain 은 ${emulator.chain} 이어야 한다`);
    }
  };

  const handle = <T>(handler: (request: FastifyRequest) => Promise<T>) =>
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        authenticate(request);
        return await reply.send(ok(await handler(request)));
      } catch (error) {
        const translated = translate(error);
        if (translated.code === 'INTERNAL_ERROR') request.log.error({ err: error }, '처리되지 않은 오류');
        return reply.status(translated.http).send(translated.envelope());
      }
    };

  /** 블록 높이를 트랜잭션별로 붙인다. 조회가 안 되면 0 이다. */
  const heights = async (txIds: string[]): Promise<Map<string, bigint | undefined>> => {
    const unique = [...new Set(txIds)];
    const found = await Promise.all(unique.map((txId) => storage.blockNumber(txId)));
    return new Map(unique.map((txId, index) => [txId, found[index]]));
  };

  /**
   * 키의 이력을 오래된 순으로 돌려준다.
   *
   * Fabric 의 GetHistoryForKey 는 최신순이고 타임스탬프는 초 단위라 같은 초에 두 번 쓰이면
   * 구분이 안 된다. 같은 키는 한 블록에 한 번만 쓰이므로 블록 번호가 엄밀한 순서다.
   * 원장이 돌려주는 순서에 기대지 않고 블록 번호(없으면 시각)로 정렬한다.
   */
  const orderedHistory = async (key: string) => {
    const entries = parseJson<FabricHistoryRecord[]>(await storage.evaluate('GetDataHistory', [key]));
    const heightByTx = await heights(entries.map((entry) => entry.txId));
    return entries
      .map((entry) => ({ entry, height: heightByTx.get(entry.txId) }))
      .sort((a, b) => {
        if (a.height !== undefined && b.height !== undefined && a.height !== b.height) {
          return a.height < b.height ? -1 : 1;
        }
        return a.entry.timestamp.localeCompare(b.entry.timestamp);
      });
  };

  app.post('/projects', handle(async () =>
    projects.list().map((project) => ({
      contract: project.contract,
      data: { project_id: project.project_id, project_name: project.project_name },
      tx: project.tx,
      issued: project.issued,
      owner: project.owner,
    })),
  ));

  app.post('/regist_project', handle(async (request) => {
    const body = registProjectBody.parse(request.body);
    const now = new Date();
    const remove = String(body.operation) === '1';
    const project = remove
      ? await projects.remove(body.project_id ?? '', now)
      : await projects.register(
          (body.project_name ?? '').trim() || (() => { throw new EmulatorError('VALIDATION_FAILED', 'project_name 이 필요하다'); })(),
          now,
        );
    if (!project) throw new EmulatorError('PROJECT_NOT_FOUND', `project ${body.project_id} does not exist`);

    const height = Number((await storage.chainHeight()) ?? 0n);
    const contractSeed = `contract|${project.project_id}`;
    const contractHashes = operationHashes(`create-contract|${project.project_id}`);
    const registerHashes = project.tx;
    const contractFact = {
      hash: contractHashes.fact_hash,
      token: factToken(now),
      _hint: 'mitum-extension-create-contract-account-operation-fact-v0.0.1',
      sender: project.owner,
      items: [
        {
          _hint: 'mitum-extension-create-contract-account-multiple-amounts-v0.0.1',
          keys: {
            _hint: 'mitum-currency-keys-v0.0.1',
            hash: project.contract.slice(2, 42),
            keys: [{ _hint: 'mitum-currency-key-v0.0.1', weight: 100, key: publicKey(contractSeed) }],
            threshold: 100,
          },
          amounts: [{ amount: '1', currency: 'FACT', _hint: 'mitum-currency-amount-v0.0.1' }],
        },
      ],
    };
    const contractOperation = {
      hash: contractHashes.hash,
      fact: contractFact,
      signs: [{ signed_at: now.toISOString(), signer: publicKey(emulator.ownerSeed), signature: signature(contractSeed) }],
      _hint: 'mitum-extension-create-contract-account-operation-v0.0.1',
    };
    const storageFact = {
      hash: registerHashes.fact_hash,
      token: factToken(now),
      _hint: remove ? 'mitum-storage-remove-model-operation-fact-v0.0.1' : 'mitum-storage-register-model-operation-fact-v0.0.1',
      sender: project.owner,
      contract: project.contract,
      project: project.project_name,
      currency: 'FACT',
    };
    const storageOperation = {
      hash: registerHashes.hash,
      fact: storageFact,
      signs: [{ signed_at: now.toISOString(), signer: publicKey(emulator.ownerSeed), signature: signature(`register|${project.project_id}`) }],
      _hint: remove ? 'mitum-storage-remove-model-operation-v0.0.1' : 'mitum-storage-register-model-operation-v0.0.1',
    };
    const receipt = (hash: string, operation: unknown, index: number) => ({
      _hint: 'mitum-currency-operation-value-v0.0.1',
      hash,
      operation,
      height,
      confirmed_at: now.toISOString(),
      reason: '',
      in_state: true,
      index,
    });
    return {
      owner: project.owner,
      contract: {
        // 에뮬레이터가 만든 형식상의 키다. 실제 키가 아니며 어디에도 쓰이지 않는다.
        data: { privatekey: privateKey(contractSeed), publickey: publicKey(contractSeed), address: project.contract },
        tx: contractHashes,
        issued: project.issued,
        response: contractOperation,
        receipt: receipt(contractHashes.fact_hash, contractOperation, 1),
      },
      storage: {
        data: { project_id: project.project_id, project_name: project.project_name },
        tx: registerHashes,
        issued: project.issued,
        response: storageOperation,
        receipt: receipt(registerHashes.fact_hash, storageOperation, 0),
      },
    };
  }));

  app.post('/create_data', handle(async (request) => {
    const body = createDataBody.parse(request.body);
    const project = requireProject(body.project_id);
    const issuedAt = new Date();
    const result = await storage.submit('CreateData', [body.data_key, body.data_value]);
    return dataOperationResponse('create', project, body.data_key, body.data_value, result, issuedAt, emulator.ownerSeed);
  }));

  app.post('/update_data', handle(async (request) => {
    const body = updateDataBody.parse(request.body);
    const project = requireProject(body.project_id);
    const issuedAt = new Date();
    const result = await storage.submit('UpdateData', [body.data_key, body.data_value]);
    return dataOperationResponse('update', project, body.data_key, body.data_value, result, issuedAt, emulator.ownerSeed);
  }));

  app.post('/delete_data', handle(async (request) => {
    const body = keyOnlyBody.parse(request.body);
    const project = requireProject(body.project_id);
    const issuedAt = new Date();
    const result = await storage.submit('DeleteData', [body.data_key]);
    return dataOperationResponse('delete', project, body.data_key, undefined, result, issuedAt, emulator.ownerSeed);
  }));

  app.post('/get_data', handle(async (request) => {
    const body = keyOnlyBody.parse(request.body);
    const project = requireProject(body.project_id);
    // 체인코드 GetData 는 값만 주므로 operation 정보(fact_hash·시각·높이)는 가장 최근 이력에서 가져온다.
    // 삭제된 키는 GetData 가 실패하고 가장 최근 이력이 isDelete 라 deleted=true 로 응답한다.
    const ordered = await orderedHistory(body.data_key);
    const latest = ordered[ordered.length - 1];
    if (!latest) throw new EmulatorError('DATA_NOT_FOUND', `storage data ${body.data_key} does not exist`);
    if (latest.entry.isDelete) {
      return dataRecord(project, body.data_key, '', true, latest.entry.txId, latest.entry.timestamp, latest.height);
    }
    const current = parseJson<{ dataKey: string; dataValue: string }>(await storage.evaluate('GetData', [body.data_key]));
    return dataRecord(project, body.data_key, current.dataValue, false, latest.entry.txId, latest.entry.timestamp, latest.height);
  }));

  app.post('/data_history', handle(async (request) => {
    const body = historyBody.parse(request.body);
    const project = requireProject(body.project_id);
    const limit = Math.min(50, Math.max(10, toInt(body.limit, 10)));
    const reverse = toBool(body.reverse);
    const ordered = await orderedHistory(body.data_key);
    const currentHeight = Number((await storage.chainHeight()) ?? 0n);
    // 문서: offset 이 0/미지정이면 현재 블록 번호. reverse=true 는 그보다 작은 블록, false 는 큰 블록.
    const offset = toInt(body.offset, 0) || currentHeight;
    const records = ordered
      .map(({ entry, height }) => dataRecord(project, body.data_key, entry.value?.dataValue ?? '', entry.isDelete, entry.txId, entry.timestamp, height))
      .filter((record) => (reverse ? record.operation.height < offset : record.operation.height > offset));
    const page = reverse ? records.reverse() : records;
    return page.slice(0, limit);
  }));
}
