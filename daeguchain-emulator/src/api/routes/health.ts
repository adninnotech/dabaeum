import type { FastifyInstance } from 'fastify';
import type { ServerDependencies } from '../server.js';

export function registerHealthRoutes(app: FastifyInstance, deps: ServerDependencies): void {
  /** 원장 높이까지 읽어야 UP 이다. 연결만 되고 조회가 안 되는 상태를 살아 있다고 보고하지 않는다. */
  app.get('/health', async (_request, reply) => {
    const height = await deps.storage.chainHeight();
    const up = height !== undefined;
    return reply.status(up ? 200 : 503).send({
      status: up ? 'UP' : 'DOWN',
      fabric: {
        mode: deps.fabricInfo.mode,
        channel: deps.fabricInfo.channel,
        chaincode: deps.fabricInfo.chaincode,
        height: height === undefined ? null : height.toString(),
      },
    });
  });
}
