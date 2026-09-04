/**
 * 실제 Fabric 에 붙어 모듈↔원장 연동을 확인하는 스모크.
 *
 *   FABRIC_WRITE_ENABLED=true npm run smoke
 *
 * 쓰기 승인이 없으면 조회 단계까지만 하고 멈춘다. 쓰기 단계는 백엔드 스모크
 * (scripts/fabric/smoke-storage-contract.sh) 와 같은 순서다: 없는 키 확인 → CreateData →
 * GetData → UpdateData(REVOKED) → GetData → GetDataHistory 에서 A → R 이력 확인.
 */
import 'dotenv/config';
import crypto from 'node:crypto';
import { loadConfig } from '../src/config.js';
import { createLogger } from '../src/logger.js';
import { SshTunnel } from '../src/fabric/tunnel.js';
import { openFabricConnection } from '../src/fabric/gateway.js';
import { FabricStorageGateway } from '../src/fabric/storage.js';
import { decodeStorageValue, encodeStorageValue } from '../src/fabric/storage-value.js';

const config = loadConfig();
const log = createLogger(config.logLevel);
const tunnel = config.tunnel.enabled ? new SshTunnel(config.tunnel, log) : undefined;

try {
  await tunnel?.start();
  const connection = await openFabricConnection(config.fabric, log);
  const storage = new FabricStorageGateway(
    connection,
    { writeEnabled: config.fabric.writeEnabled, channelName: config.fabric.channelName },
    log,
  );

  const height = await storage.chainHeight();
  log.info({ height: height?.toString() ?? null }, '1. 블록 높이');

  const key = randomKey();
  const history = JSON.parse(new TextDecoder().decode(await storage.evaluate('GetDataHistory', [key])));
  if (!Array.isArray(history) || history.length !== 0) {
    throw new Error(`무작위 키 ${key} 에 이력이 있다. 다시 실행하라`);
  }
  log.info({ key }, '2. 미사용 키 확인');

  if (!config.fabric.writeEnabled) {
    log.warn('FABRIC_WRITE_ENABLED=false 라 조회 단계까지만 확인했다');
  } else {
    const hash = crypto.createHash('sha256').update(`smoke-${key}`).digest('hex');
    const active = encodeStorageValue({ schemaVersion: 1, status: 'ACTIVE', vcHash: hash, eventTime: new Date() });
    const created = await storage.submit('CreateData', [key, active]);
    log.info({ tx: created.transactionId, code: created.resultCode, block: created.blockNumber.toString() }, '3. CreateData');
    if (!created.confirmed) throw new Error('CreateData 가 확정되지 않았다');

    const read = JSON.parse(new TextDecoder().decode(await storage.evaluate('GetData', [key])));
    if (read.dataValue !== active) throw new Error('GetData 값이 기록한 값과 다르다');
    log.info({ status: decodeStorageValue(read.dataValue).status }, '4. GetData');

    const revoked = encodeStorageValue({ schemaVersion: 1, status: 'REVOKED', vcHash: hash, eventTime: new Date() });
    const updated = await storage.submit('UpdateData', [key, revoked]);
    log.info({ tx: updated.transactionId, code: updated.resultCode }, '5. UpdateData(REVOKED)');
    if (!updated.confirmed) throw new Error('UpdateData 가 확정되지 않았다');

    const after = JSON.parse(new TextDecoder().decode(await storage.evaluate('GetDataHistory', [key])));
    // Fabric 은 이력을 최신순으로 준다. 시각 기준 오래된 순으로 정렬해 A → R 을 확인한다.
    const statuses = (after as Array<{ timestamp: string; value?: { dataValue: string } }>)
      .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
      .map((entry) => (entry.value ? decodeStorageValue(entry.value.dataValue).status : 'DELETED'));
    log.info({ statuses }, '6. GetDataHistory');
    if (statuses.join(',') !== 'ACTIVE,REVOKED') throw new Error(`이력이 A → R 이 아니다: ${statuses.join(',')}`);
  }

  log.info('스모크 통과');
  connection.close();
} finally {
  await tunnel?.close();
}

function randomKey(): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  const bytes = crypto.randomBytes(16);
  return Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join('');
}
