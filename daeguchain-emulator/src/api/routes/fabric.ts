import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { ServerDependencies } from '../server.js';

const invocation = z.object({
  function: z.string().min(1).max(100),
  arguments: z.array(z.string()).max(20).default([]),
});

function decodePayload(payload: Uint8Array): { payload: string; payloadJson: unknown } {
  const text = new TextDecoder().decode(payload);
  let payloadJson: unknown = null;
  try {
    payloadJson = text.length > 0 ? JSON.parse(text) : null;
  } catch {
    payloadJson = null;
  }
  return { payload: text, payloadJson };
}

/**
 * 체인코드 함수를 그대로 부르는 통과 경로. 모듈↔Fabric 연동을 확인하는 용도이며
 * 백엔드가 쓰는 계약이 아니다. 대구체인 계약이 확정되면 제거하거나 관리자 전용으로 내린다.
 */
export function registerFabricRoutes(app: FastifyInstance, deps: ServerDependencies): void {
  app.post('/fabric/evaluate', async (request) => {
    const body = invocation.parse(request.body);
    const payload = await deps.storage.evaluate(body.function, body.arguments);
    return decodePayload(payload);
  });

  app.post('/fabric/submit', async (request) => {
    const body = invocation.parse(request.body);
    const result = await deps.storage.submit(body.function, body.arguments);
    return {
      transactionId: result.transactionId,
      confirmed: result.confirmed,
      resultCode: result.resultCode,
      blockNumber: result.blockNumber.toString(),
      ...decodePayload(result.payload),
    };
  });
}
