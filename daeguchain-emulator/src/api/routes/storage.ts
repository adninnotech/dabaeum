import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { ServerDependencies } from '../server.js';
import type { SubmitResult } from '../../fabric/storage.js';
import {
  STORAGE_KEY_PATTERN,
  decodeStorageValue,
  encodeStorageValue,
  type RegistryStatus,
} from '../../fabric/storage-value.js';
import { ApiError, notFound } from '../errors.js';
import { FabricGatewayError } from '../../fabric/errors.js';

const keyParam = z.object({ key: z.string().regex(STORAGE_KEY_PATTERN, '키는 대문자·숫자 16자다') });

const statusSchema = z.enum(['ACTIVE', 'REVOKED', 'SUPERSEDED']);
const hashSchema = z.string().regex(/^[0-9a-f]{64}$/, 'vcHash 는 소문자 hex 64자다');
const eventTimeSchema = z
  .string()
  .datetime({ offset: true })
  .transform((value) => new Date(value));

const createBody = z.object({
  key: z.string().regex(STORAGE_KEY_PATTERN, '키는 대문자·숫자 16자다'),
  status: statusSchema.default('ACTIVE'),
  vcHash: hashSchema,
  eventTime: eventTimeSchema,
});

const updateBody = z.object({
  status: statusSchema,
  vcHash: hashSchema,
  eventTime: eventTimeSchema,
});

interface StorageDataRecord {
  dataKey: string;
  dataValue: string;
}

interface StorageHistoryRecord {
  txId: string;
  timestamp: string;
  isDelete: boolean;
  value?: StorageDataRecord;
}

/** 백엔드 BlockchainReceipt 가 요구하는 필드. */
function receipt(result: SubmitResult) {
  return {
    transactionId: result.transactionId,
    confirmed: result.confirmed,
    resultCode: result.resultCode,
    blockNumber: result.blockNumber.toString(),
  };
}

function presentValue(key: string, record: StorageDataRecord) {
  if (record.dataKey !== key) {
    throw new ApiError(502, 'FABRIC_READ_FAILED', '원장 응답의 키가 요청과 다르다');
  }
  const value = decodeStorageValue(record.dataValue);
  return {
    key,
    schemaVersion: value.schemaVersion,
    status: value.status,
    vcHash: value.vcHash,
    eventTime: value.eventTime.toISOString(),
    raw: record.dataValue,
  };
}

function parseJson<T>(payload: Uint8Array): T {
  return JSON.parse(new TextDecoder().decode(payload)) as T;
}

/**
 * 대구체인 Storage 와 비슷한 모양의 키-값 API. 체인코드의 CreateData/GetData/UpdateData/
 * GetDataHistory 에 대응한다. 값 형식은 백엔드 StorageValue(1|A|hash|epochMillis) 그대로다.
 */
export function registerStorageRoutes(app: FastifyInstance, deps: ServerDependencies): void {
  app.post('/storage/data', async (request, reply) => {
    const body = createBody.parse(request.body);
    const encoded = encodeStorageValue({
      schemaVersion: 1,
      status: body.status,
      vcHash: body.vcHash,
      eventTime: truncateToMillis(body.eventTime),
    });
    const result = await deps.storage.submit('CreateData', [body.key, encoded]);
    return reply.status(201).send({ key: body.key, value: encoded, ...receipt(result) });
  });

  app.get('/storage/data/:key', async (request) => {
    const { key } = keyParam.parse(request.params);
    let record: StorageDataRecord;
    try {
      record = parseJson<StorageDataRecord>(await deps.storage.evaluate('GetData', [key]));
    } catch (error) {
      // 체인코드는 미존재와 장애를 같은 오류로 돌려준다. 백엔드 Java 와 같은 방식으로
      // 이력이 비어 있을 때만 "없음" 으로 판정하고, 이력 조회마저 실패하면 장애로 본다.
      if (error instanceof FabricGatewayError && (await hasNoHistory(deps, key))) {
        throw notFound(`storage data ${key} does not exist`);
      }
      throw error;
    }
    return presentValue(key, record);
  });

  app.patch('/storage/data/:key', async (request) => {
    const { key } = keyParam.parse(request.params);
    const body = updateBody.parse(request.body);
    const encoded = encodeStorageValue({
      schemaVersion: 1,
      status: body.status as RegistryStatus,
      vcHash: body.vcHash,
      eventTime: truncateToMillis(body.eventTime),
    });
    const result = await deps.storage.submit('UpdateData', [key, encoded]);
    return { key, value: encoded, ...receipt(result) };
  });

  app.get('/storage/data/:key/history', async (request) => {
    const { key } = keyParam.parse(request.params);
    const entries = parseJson<StorageHistoryRecord[]>(await deps.storage.evaluate('GetDataHistory', [key]));
    return entries.map((entry) => ({
      transactionId: entry.txId,
      timestamp: entry.timestamp,
      deleted: entry.isDelete,
      value: entry.isDelete || !entry.value ? null : presentValue(key, entry.value),
    }));
  });

  app.get('/storage/height', async (_request, reply) => {
    const height = await deps.storage.chainHeight();
    if (height === undefined) {
      throw new ApiError(503, 'FABRIC_GATEWAY_CONNECTION_FAILED', '블록 높이를 조회할 수 없다');
    }
    return reply.send({ height: height.toString() });
  });
}

async function hasNoHistory(deps: ServerDependencies, key: string): Promise<boolean> {
  try {
    const history = parseJson<unknown>(await deps.storage.evaluate('GetDataHistory', [key]));
    return Array.isArray(history) && history.length === 0;
  } catch {
    return false;
  }
}

/** 원장 값의 시각은 epochMillis 하나뿐이다. 백엔드도 발급 시각을 밀리초로 내려서 기록한다. */
function truncateToMillis(date: Date): Date {
  return new Date(Math.floor(date.getTime()));
}
