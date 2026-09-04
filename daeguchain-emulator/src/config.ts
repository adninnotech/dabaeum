import { z } from 'zod';
import os from 'node:os';
import path from 'node:path';

const bool = z
  .string()
  .transform((value) => value.trim().toLowerCase())
  .pipe(z.enum(['true', 'false', '1', '0', 'yes', 'no']))
  .transform((value) => value === 'true' || value === '1' || value === 'yes');

const port = z.coerce.number().int().min(1).max(65535);
const millis = z.coerce.number().int().positive();

const schema = z.object({
  MODULE_HOST: z.string().default('127.0.0.1'),
  MODULE_PORT: port.default(8090),
  MODULE_API_KEY: z.string().optional(),
  EMULATOR_TOKEN: z.string().optional(),
  EMULATOR_CHAIN: z.string().min(1).default('dchain'),
  EMULATOR_OWNER_SEED: z.string().min(1).default('dabaeum-daeguchain-emulator'),
  EMULATOR_DATA_DIR: z.string().min(1).default('./data'),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),

  FABRIC_WRITE_ENABLED: bool.default('false'),

  FABRIC_TUNNEL_ENABLED: bool.default('true'),
  FABRIC_SSH_HOST: z.string().optional(),
  FABRIC_SSH_PORT: port.default(22),
  FABRIC_SSH_USERNAME: z.string().optional(),
  FABRIC_SSH_PASSWORD: z.string().optional(),
  FABRIC_SSH_KNOWN_HOSTS: z.string().optional(),
  FABRIC_LOCAL_PORT: port.default(7051),
  FABRIC_REMOTE_HOST: z.string().default('127.0.0.1'),
  FABRIC_REMOTE_PORT: port.default(7051),

  FABRIC_OVERRIDE_AUTHORITY: z.string().min(1),
  FABRIC_MSP_ID: z.string().min(1),
  FABRIC_CHANNEL: z.string().min(1),
  FABRIC_CHAINCODE: z.string().min(1),
  FABRIC_CERTIFICATE_PATH: z.string().min(1),
  FABRIC_PRIVATE_KEY_PATH: z.string().min(1),
  FABRIC_TLS_CA_PATH: z.string().min(1),
  FABRIC_EVALUATE_TIMEOUT_MS: millis.default(5_000),
  FABRIC_ENDORSE_TIMEOUT_MS: millis.default(15_000),
  FABRIC_SUBMIT_TIMEOUT_MS: millis.default(5_000),
  FABRIC_COMMIT_TIMEOUT_MS: millis.default(60_000),
});

export interface TunnelConfig {
  readonly enabled: boolean;
  readonly sshHost: string;
  readonly sshPort: number;
  readonly sshUsername: string;
  readonly sshPassword: string;
  readonly knownHostsPath: string;
  readonly localPort: number;
  readonly remoteHost: string;
  readonly remotePort: number;
}

export interface FabricConfig {
  readonly writeEnabled: boolean;
  readonly localPort: number;
  readonly overrideAuthority: string;
  readonly mspId: string;
  readonly channelName: string;
  readonly chaincodeName: string;
  readonly certificatePath: string;
  readonly privateKeyPath: string;
  readonly tlsCaPath: string;
  readonly evaluateTimeoutMs: number;
  readonly endorseTimeoutMs: number;
  readonly submitTimeoutMs: number;
  readonly commitTimeoutMs: number;
}

export interface EmulatorConfig {
  readonly token: string | undefined;
  readonly chain: string;
  readonly ownerSeed: string;
  readonly dataDir: string;
}

export interface ModuleConfig {
  readonly host: string;
  readonly port: number;
  readonly apiKey: string | undefined;
  readonly emulator: EmulatorConfig;
  readonly logLevel: string;
  readonly tunnel: TunnelConfig;
  readonly fabric: FabricConfig;
}

/**
 * 환경변수를 검증해 설정 객체로 만든다. 비밀값은 여기서만 읽고 로그에는 남기지 않는다.
 * 터널을 켠 경우에만 SSH 항목을 요구한다.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): ModuleConfig {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    const issues = parsed.error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`);
    throw new Error(`설정이 올바르지 않습니다:\n  ${issues.join('\n  ')}`);
  }
  const e = parsed.data;

  if (e.FABRIC_TUNNEL_ENABLED) {
    const missing = (['FABRIC_SSH_HOST', 'FABRIC_SSH_USERNAME', 'FABRIC_SSH_PASSWORD'] as const)
      .filter((key) => !e[key]);
    if (missing.length > 0) {
      throw new Error(`터널을 켰으면 다음 값이 필요합니다: ${missing.join(', ')}`);
    }
  }

  return {
    host: e.MODULE_HOST,
    port: e.MODULE_PORT,
    apiKey: e.MODULE_API_KEY?.trim() || undefined,
    emulator: {
      token: e.EMULATOR_TOKEN?.trim() || undefined,
      chain: e.EMULATOR_CHAIN,
      ownerSeed: e.EMULATOR_OWNER_SEED,
      dataDir: path.resolve(e.EMULATOR_DATA_DIR),
    },
    logLevel: e.LOG_LEVEL,
    tunnel: {
      enabled: e.FABRIC_TUNNEL_ENABLED,
      sshHost: e.FABRIC_SSH_HOST ?? '',
      sshPort: e.FABRIC_SSH_PORT,
      sshUsername: e.FABRIC_SSH_USERNAME ?? '',
      sshPassword: e.FABRIC_SSH_PASSWORD ?? '',
      knownHostsPath: e.FABRIC_SSH_KNOWN_HOSTS?.trim() || path.join(os.homedir(), '.ssh', 'known_hosts'),
      localPort: e.FABRIC_LOCAL_PORT,
      remoteHost: e.FABRIC_REMOTE_HOST,
      remotePort: e.FABRIC_REMOTE_PORT,
    },
    fabric: {
      writeEnabled: e.FABRIC_WRITE_ENABLED,
      localPort: e.FABRIC_LOCAL_PORT,
      overrideAuthority: e.FABRIC_OVERRIDE_AUTHORITY,
      mspId: e.FABRIC_MSP_ID,
      channelName: e.FABRIC_CHANNEL,
      chaincodeName: e.FABRIC_CHAINCODE,
      certificatePath: e.FABRIC_CERTIFICATE_PATH,
      privateKeyPath: e.FABRIC_PRIVATE_KEY_PATH,
      tlsCaPath: e.FABRIC_TLS_CA_PATH,
      evaluateTimeoutMs: e.FABRIC_EVALUATE_TIMEOUT_MS,
      endorseTimeoutMs: e.FABRIC_ENDORSE_TIMEOUT_MS,
      submitTimeoutMs: e.FABRIC_SUBMIT_TIMEOUT_MS,
      commitTimeoutMs: e.FABRIC_COMMIT_TIMEOUT_MS,
    },
  };
}
