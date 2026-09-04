import net from 'node:net';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { Client, type ConnectConfig } from 'ssh2';
import type { TunnelConfig } from '../config.js';
import type { Logger } from '../logger.js';
import { FabricGatewayError } from './errors.js';

/**
 * peer 와 다른 장비에서 구동할 때 쓰는 SSH 로컬 포워딩.
 * 127.0.0.1:{localPort} → (SSH) → {remoteHost}:{remotePort}
 *
 * 백엔드 Java 터널은 유휴 10분 뒤 세션이 끊기면 복구되지 않아 이후 호출이 전부 실패했다.
 * 여기서는 keepalive 를 보내고, 끊기면 다음 연결 시도에서 SSH 세션을 다시 연다.
 */
export class SshTunnel {
  private client: Client | undefined;
  private server: net.Server | undefined;
  private connecting: Promise<Client> | undefined;

  constructor(
    private readonly config: TunnelConfig,
    private readonly log: Logger,
  ) {}

  async start(): Promise<void> {
    if (this.server) return;
    await this.sshClient();
    this.server = net.createServer((socket) => this.forward(socket));
    await new Promise<void>((resolve, reject) => {
      this.server!.once('error', reject);
      this.server!.listen(this.config.localPort, '127.0.0.1', () => {
        this.server!.off('error', reject);
        resolve();
      });
    });
    this.log.info(
      { localPort: this.config.localPort, remote: `${this.config.remoteHost}:${this.config.remotePort}` },
      'SSH 터널을 열었다',
    );
  }

  async close(): Promise<void> {
    await new Promise<void>((resolve) => (this.server ? this.server.close(() => resolve()) : resolve()));
    this.server = undefined;
    this.client?.end();
    this.client = undefined;
    this.connecting = undefined;
  }

  private async forward(socket: net.Socket): Promise<void> {
    try {
      const client = await this.sshClient();
      client.forwardOut(
        socket.localAddress ?? '127.0.0.1',
        socket.localPort ?? 0,
        this.config.remoteHost,
        this.config.remotePort,
        (error, stream) => {
          if (error) {
            this.log.warn({ err: error }, 'SSH 포워딩 실패. 세션을 버리고 다음 연결에서 다시 연다');
            this.dropClient();
            socket.destroy();
            return;
          }
          socket.pipe(stream).pipe(socket);
          stream.on('error', () => socket.destroy());
          socket.on('error', () => stream.destroy());
        },
      );
    } catch (error) {
      this.log.warn({ err: error }, 'SSH 세션을 열지 못했다');
      socket.destroy();
    }
  }

  private sshClient(): Promise<Client> {
    if (this.client) return Promise.resolve(this.client);
    if (this.connecting) return this.connecting;
    this.connecting = this.connect().finally(() => {
      this.connecting = undefined;
    });
    return this.connecting;
  }

  private async connect(): Promise<Client> {
    const knownHosts = await loadKnownHosts(this.config.knownHostsPath);
    const hostEntry = knownHostsAlias(this.config.sshHost, this.config.sshPort);
    const client = new Client();
    const connectConfig: ConnectConfig = {
      host: this.config.sshHost,
      port: this.config.sshPort,
      username: this.config.sshUsername,
      password: this.config.sshPassword,
      readyTimeout: 10_000,
      keepaliveInterval: 30_000,
      keepaliveCountMax: 3,
      hostVerifier: (key: Buffer) => {
        const accepted = knownHosts.some((entry) => entry.matches(hostEntry, this.config.sshHost, key));
        if (!accepted) {
          this.log.error({ host: hostEntry }, 'known_hosts 에 없는 호스트 키다. 연결을 거부한다');
        }
        return accepted;
      },
    };
    await new Promise<void>((resolve, reject) => {
      client.once('ready', resolve);
      client.once('error', reject);
      client.connect(connectConfig);
    });
    client.on('close', () => {
      this.log.warn('SSH 세션이 닫혔다. 다음 연결에서 다시 연다');
      if (this.client === client) this.client = undefined;
    });
    client.on('error', (error) => {
      this.log.warn({ err: error }, 'SSH 세션 오류');
      if (this.client === client) this.client = undefined;
    });
    this.client = client;
    this.log.info({ host: hostEntry }, 'SSH 세션을 열었다');
    return client;
  }

  private dropClient(): void {
    this.client?.end();
    this.client = undefined;
  }
}

function knownHostsAlias(host: string, port: number): string {
  return port === 22 ? host : `[${host}]:${port}`;
}

interface KnownHostEntry {
  matches(alias: string, host: string, key: Buffer): boolean;
}

/**
 * OpenSSH known_hosts 를 읽는다. 평문 호스트와 해시(|1|salt|hash) 항목을 모두 지원한다.
 * 키 비교는 base64 로 인코딩된 공개키 본문으로 한다.
 */
async function loadKnownHosts(filePath: string): Promise<KnownHostEntry[]> {
  let text: string;
  try {
    text = await fs.readFile(filePath, 'utf8');
  } catch (error) {
    throw new FabricGatewayError(
      'FABRIC_GATEWAY_CONNECTION_FAILED',
      `known_hosts 파일을 읽을 수 없다: ${filePath}`,
      { cause: error },
    );
  }
  const entries: KnownHostEntry[] = [];
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const parts = line.split(/\s+/);
    if (parts.length < 3) continue;
    const [hosts, , keyBase64] = parts as [string, string, string];
    entries.push({
      matches(alias, host, key) {
        if (key.toString('base64') !== keyBase64) return false;
        return hosts.split(',').some((pattern) => hostMatches(pattern, alias, host));
      },
    });
  }
  return entries;
}

function hostMatches(pattern: string, alias: string, host: string): boolean {
  if (pattern.startsWith('|1|')) {
    const [, , saltBase64, hashBase64] = pattern.split('|');
    if (!saltBase64 || !hashBase64) return false;
    const salt = Buffer.from(saltBase64, 'base64');
    return [alias, host].some(
      (candidate) => crypto.createHmac('sha1', salt).update(candidate).digest('base64') === hashBase64,
    );
  }
  return pattern === alias || pattern === host;
}
