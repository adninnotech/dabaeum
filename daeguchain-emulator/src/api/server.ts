import Fastify, { type FastifyBaseLogger, type FastifyInstance } from 'fastify';
import type { Logger } from '../logger.js';
import type { StorageGateway } from '../fabric/storage.js';
import type { ProjectStore } from '../emulator/projects.js';
import { ApiError, errorHandler } from './errors.js';
import { registerHealthRoutes } from './routes/health.js';
import { registerFabricRoutes } from './routes/fabric.js';
import { registerDaeguChainRoutes } from './routes/daeguchain.js';

export interface EmulatorIdentity {
  /** 요청 본문 token 과 비교할 값. 비우면 검사하지 않는다. */
  readonly token: string | undefined;
  /** 요청 본문 chain 이 이 값이어야 한다. 문서 기본값 dchain */
  readonly chain: string;
  /** owner 주소·서명자 공개키를 파생하는 시드 */
  readonly ownerSeed: string;
}

export interface ServerDependencies {
  readonly storage: StorageGateway;
  readonly projects: ProjectStore;
  readonly emulator: EmulatorIdentity;
  readonly log: Logger;
  /** /fabric/* 통과 경로용. 비우면 검사하지 않는다. */
  readonly apiKey: string | undefined;
  readonly fabricInfo: { readonly channel: string; readonly chaincode: string; readonly mode: 'fabric' | 'in-memory' };
}

/** 대구체인 BaaS 의 경로 접두. 백엔드는 호스트만 바꾸면 실제 대구체인으로 옮겨간다. */
export const DAEGUCHAIN_STORAGE_PREFIX = '/daeguchain/v2/mitum/storage';

export function buildServer(deps: ServerDependencies): FastifyInstance {
  // pino 와 fastify 가 서로 다른 pino 타입 버전을 참조해 인스턴스 타입이 어긋난다. 런타임은 같은 pino 다.
  const app = Fastify({ logger: deps.log as unknown as FastifyBaseLogger });

  app.setErrorHandler(errorHandler);

  // /fabric/* 통과 경로만 헤더 키로 잠근다. 대구체인 경로는 문서대로 본문 token 으로 검사한다.
  app.addHook('onRequest', async (request) => {
    if (!deps.apiKey || !request.url.startsWith('/fabric/')) return;
    if (request.headers['x-api-key'] !== deps.apiKey) {
      throw new ApiError(401, 'UNAUTHORIZED', 'X-Api-Key 헤더가 없거나 틀리다');
    }
  });

  registerHealthRoutes(app, deps);
  registerFabricRoutes(app, deps);
  app.register(async (scope) => registerDaeguChainRoutes(scope, deps), { prefix: DAEGUCHAIN_STORAGE_PREFIX });

  return app;
}
