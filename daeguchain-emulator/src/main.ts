// 서버 Node 18 에는 --env-file 이 없어 코드에서 .env 를 읽는다. 파일이 없으면 조용히 넘어간다.
import 'dotenv/config';
import fs from 'node:fs/promises';
import { loadConfig } from './config.js';
import { createLogger } from './logger.js';
import { SshTunnel } from './fabric/tunnel.js';
import { openFabricConnection, type FabricConnection } from './fabric/gateway.js';
import { FabricStorageGateway, type StorageGateway } from './fabric/storage.js';
import { InMemoryStorageGateway } from './fabric/fake-storage.js';
import { buildServer } from './api/server.js';
import { ProjectStore } from './emulator/projects.js';
import { address } from './emulator/mitum.js';
import path from 'node:path';

/**
 * 기동 순서: 설정 → (터널) → Fabric Gateway → HTTP.
 * Fabric 에 붙지 못하면 기동을 실패시킨다. 연결 없이 떠 있는 모듈은 백엔드를 속인다.
 *
 * FABRIC_MODE=in-memory 로 두면 Fabric 없이 메모리 원장으로 API 만 확인할 수 있다.
 */
async function main(): Promise<void> {
  const inMemory = process.env['FABRIC_MODE'] === 'in-memory';
  const config = loadConfig(inMemory ? withFabricPlaceholders(process.env) : process.env);
  const log = createLogger(config.logLevel);

  let tunnel: SshTunnel | undefined;
  let connection: FabricConnection | undefined;
  let storage: StorageGateway;

  if (inMemory) {
    log.warn('FABRIC_MODE=in-memory: 메모리 원장으로 구동한다. 실제 Fabric 에는 아무것도 기록되지 않는다');
    storage = new InMemoryStorageGateway(config.fabric.writeEnabled);
  } else {
    await requireReadable({
      FABRIC_CERTIFICATE_PATH: config.fabric.certificatePath,
      FABRIC_PRIVATE_KEY_PATH: config.fabric.privateKeyPath,
      FABRIC_TLS_CA_PATH: config.fabric.tlsCaPath,
    });
    if (config.tunnel.enabled) {
      tunnel = new SshTunnel(config.tunnel, log);
      await tunnel.start();
    }
    connection = await openFabricConnection(config.fabric, log);
    storage = new FabricStorageGateway(
      connection,
      { writeEnabled: config.fabric.writeEnabled, channelName: config.fabric.channelName },
      log,
    );
    const height = await storage.chainHeight();
    if (height === undefined) {
      throw new Error('Fabric 에 연결됐지만 블록 높이를 읽지 못했다. 채널 이름과 인증 자료를 확인하라');
    }
    log.info({ height: height.toString(), writeEnabled: config.fabric.writeEnabled }, 'Fabric 원장 확인');
  }

  const projects = new ProjectStore(
    path.join(config.emulator.dataDir, 'projects.json'),
    address(`owner|${config.emulator.ownerSeed}`),
  );
  await projects.load();
  log.info({ projects: projects.list().length, dataDir: config.emulator.dataDir }, 'Storage 프로젝트 저장소 로드');

  const app = buildServer({
    storage,
    projects,
    emulator: { token: config.emulator.token, chain: config.emulator.chain, ownerSeed: config.emulator.ownerSeed },
    log,
    apiKey: config.apiKey,
    fabricInfo: {
      channel: config.fabric.channelName,
      chaincode: config.fabric.chaincodeName,
      mode: inMemory ? 'in-memory' : 'fabric',
    },
  });

  const shutdown = async (signal: string) => {
    log.info({ signal }, '종료한다');
    await app.close();
    connection?.close();
    await tunnel?.close();
    process.exit(0);
  };
  process.once('SIGINT', () => void shutdown('SIGINT'));
  process.once('SIGTERM', () => void shutdown('SIGTERM'));

  await app.listen({ host: config.host, port: config.port });
}

/** 인증 자료가 없으면 SDK 오류 대신 어느 설정값의 파일이 없는지로 실패시킨다. 값(내용)은 읽지 않는다. */
async function requireReadable(paths: Record<string, string>): Promise<void> {
  const missing: string[] = [];
  for (const [name, filePath] of Object.entries(paths)) {
    try {
      await fs.access(filePath, fs.constants.R_OK);
    } catch {
      missing.push(`${name}=${filePath}`);
    }
  }
  if (missing.length > 0) {
    throw new Error(`Fabric 인증 자료를 읽을 수 없다:\n  ${missing.join('\n  ')}`);
  }
}

/** in-memory 모드에서는 Fabric 필수값이 없어도 뜨도록 자리표시자를 채운다. */
function withFabricPlaceholders(env: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  return {
    FABRIC_TUNNEL_ENABLED: 'false',
    FABRIC_OVERRIDE_AUTHORITY: 'in-memory',
    FABRIC_MSP_ID: 'in-memory',
    FABRIC_CHANNEL: 'in-memory',
    FABRIC_CHAINCODE: 'in-memory',
    FABRIC_CERTIFICATE_PATH: 'in-memory',
    FABRIC_PRIVATE_KEY_PATH: 'in-memory',
    FABRIC_TLS_CA_PATH: 'in-memory',
    ...env,
  };
}

main().catch((error: unknown) => {
  // 기동 실패는 상세를 남긴다. 운영 로그에만 남고 응답으로 나가지는 않는다.
  console.error('[dabaeum-ledger-module] 기동 실패:', error instanceof Error ? error.message : error);
  if (error instanceof Error && error.cause) console.error('원인:', error.cause);
  process.exit(1);
});
