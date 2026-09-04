import type { FastifyError, FastifyReply, FastifyRequest } from 'fastify';
import { ZodError } from 'zod';
import { FabricGatewayError } from '../fabric/errors.js';

/** 백엔드가 분기하는 오류 코드. 본문은 항상 { code, message } 다. */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'FABRIC_WRITE_NOT_APPROVED'
  | 'FABRIC_READ_FAILED'
  | 'FABRIC_SUBMIT_FAILED'
  | 'FABRIC_COMMIT_TIMEOUT'
  | 'FABRIC_COMMIT_INVALID'
  | 'FABRIC_GATEWAY_CONNECTION_FAILED'
  | 'INTERNAL_ERROR';

export class ApiError extends Error {
  override readonly name = 'ApiError';

  constructor(
    readonly status: number,
    readonly code: ApiErrorCode,
    message: string,
  ) {
    super(message);
  }
}

export function notFound(message: string): ApiError {
  return new ApiError(404, 'NOT_FOUND', message);
}

export function conflict(message: string): ApiError {
  return new ApiError(409, 'CONFLICT', message);
}

/**
 * 체인코드 오류 문장을 API 코드로 바꾼다. 체인코드는 존재하지 않는 키와 중복 키를
 * 모두 일반 오류로 돌려주므로 여기서 구분해야 백엔드가 "없음"과 "장애"를 가릴 수 있다.
 */
export function translateFabricError(error: FabricGatewayError): ApiError {
  const message = error.message ?? '';
  if (/already exists/i.test(message)) return conflict(message);
  if (/does not exist|not found/i.test(message)) return notFound(message);
  switch (error.code) {
    case 'FABRIC_WRITE_NOT_APPROVED':
      return new ApiError(403, error.code, '원장 쓰기가 승인되지 않았다. FABRIC_WRITE_ENABLED 를 확인하라');
    case 'FABRIC_READ_FAILED':
      return new ApiError(502, error.code, '원장 조회에 실패했다');
    case 'FABRIC_SUBMIT_FAILED':
      return new ApiError(502, error.code, '원장 제출에 실패했다');
    case 'FABRIC_COMMIT_TIMEOUT':
      return new ApiError(504, error.code, '원장 커밋 확인이 시간 안에 끝나지 않았다');
    case 'FABRIC_COMMIT_INVALID':
      return new ApiError(502, error.code, '원장이 트랜잭션을 무효로 판정했다');
    case 'FABRIC_GATEWAY_CONNECTION_FAILED':
      return new ApiError(503, error.code, '원장에 연결할 수 없다');
  }
}

export function errorHandler(error: FastifyError | Error, request: FastifyRequest, reply: FastifyReply): void {
  if (error instanceof ApiError) {
    void reply.status(error.status).send({ code: error.code, message: error.message });
    return;
  }
  if (error instanceof FabricGatewayError) {
    const translated = translateFabricError(error);
    void reply.status(translated.status).send({ code: translated.code, message: translated.message });
    return;
  }
  if (error instanceof ZodError) {
    const detail = error.issues.map((issue) => `${issue.path.join('.') || '(body)'}: ${issue.message}`).join('; ');
    void reply.status(400).send({ code: 'VALIDATION_FAILED', message: detail });
    return;
  }
  const fastifyError = error as FastifyError;
  if (fastifyError.validation || fastifyError.statusCode === 400) {
    void reply.status(400).send({ code: 'VALIDATION_FAILED', message: error.message });
    return;
  }
  request.log.error({ err: error }, '처리되지 않은 오류');
  void reply.status(500).send({ code: 'INTERNAL_ERROR', message: '내부 오류' });
}
