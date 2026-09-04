import pino from 'pino';

/**
 * 비밀값·인증 자료 경로가 로그에 섞이지 않도록 알려진 키는 가린다.
 * Fabric SDK 오류는 원인 진단에 필요하므로 메시지는 남기되 요청 본문의 비밀 필드는 지운다.
 */
export function createLogger(level: string) {
  const pretty = process.stdout.isTTY && process.env['NODE_ENV'] !== 'production';
  return pino({
    level,
    redact: {
      paths: ['password', 'sshPassword', 'apiKey', 'privateKey', '*.password', '*.apiKey', 'req.headers["x-api-key"]'],
      censor: '[가려짐]',
    },
    ...(pretty ? { transport: { target: 'pino-pretty', options: { translateTime: 'SYS:HH:MM:ss', ignore: 'pid,hostname' } } } : {}),
  });
}

export type Logger = ReturnType<typeof createLogger>;
