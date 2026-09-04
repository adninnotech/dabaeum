/**
 * 원장에 이미 기록된 키를 블록에서 읽어 나열한다. 읽기 전용이며 쓰기 승인이 필요 없다.
 *
 *   npx tsx scripts/list-ledger-keys.ts            # 모든 블록
 *   npx tsx scripts/list-ledger-keys.ts 10 28      # 블록 범위
 *
 * 체인코드에는 키 목록 함수가 없어 qscc GetBlockByNumber 로 블록을 하나씩 받아
 * 엔도서 트랜잭션의 쓰기 집합(KVRWSet.writes)에서 우리 체인코드 네임스페이스의 키를 뽑는다.
 */
import 'dotenv/config';
import { common, ledger, peer } from '@hyperledger/fabric-protos';
import { loadConfig } from '../src/config.js';
import { createLogger } from '../src/logger.js';
import { SshTunnel } from '../src/fabric/tunnel.js';
import { openFabricConnection } from '../src/fabric/gateway.js';

const config = loadConfig();
const log = createLogger('warn');
const tunnel = config.tunnel.enabled ? new SshTunnel(config.tunnel, log) : undefined;

interface WriteRecord {
  block: number;
  txId: string;
  valid: boolean;
  key: string;
  deleted: boolean;
  value: string;
}

try {
  await tunnel?.start();
  const connection = await openFabricConnection(config.fabric, log);
  const qscc = connection.qscc;
  const channel = config.fabric.channelName;

  const info = common.BlockchainInfo.deserializeBinary(await qscc.evaluateTransaction('GetChainInfo', channel));
  const height = info.getHeight();
  const from = Number(process.argv[2] ?? 0);
  const to = Number(process.argv[3] ?? height - 1);

  const records: WriteRecord[] = [];
  const namespaces = new Map<string, number>();
  const blockSummary: string[] = [];
  for (let number = from; number <= to; number += 1) {
    const block = common.Block.deserializeBinary(await qscc.evaluateTransaction('GetBlockByNumber', channel, String(number)));
    const filter = block.getMetadata()?.getMetadataList_asU8()[common.BlockMetadataIndex.TRANSACTIONS_FILTER] ?? new Uint8Array();
    const types: string[] = [];
    block.getData()?.getDataList_asU8().forEach((raw, index) => {
      const envelope = common.Envelope.deserializeBinary(raw);
      const payload = common.Payload.deserializeBinary(envelope.getPayload_asU8());
      const header = common.ChannelHeader.deserializeBinary(payload.getHeader()?.getChannelHeader_asU8() ?? new Uint8Array());
      types.push(Object.entries(common.HeaderType).find(([, v]) => v === header.getType())?.[0] ?? String(header.getType()));
      if (header.getType() !== common.HeaderType.ENDORSER_TRANSACTION) return;
      const txId = header.getTxId();
      const valid = (filter[index] ?? 0) === peer.TxValidationCode.VALID;
      const transaction = peer.Transaction.deserializeBinary(payload.getData_asU8());
      if (process.env['DEBUG']) {
        console.log(`  [debug] block ${number} tx#${index} ${txId.slice(0, 8)} payloadData=${payload.getData_asU8().length}B actions=${transaction.getActionsList().length}`);
      }
      for (const action of transaction.getActionsList()) {
        const actionPayload = peer.ChaincodeActionPayload.deserializeBinary(action.getPayload_asU8());
        const responsePayload = peer.ProposalResponsePayload.deserializeBinary(
          actionPayload.getAction()?.getProposalResponsePayload_asU8() ?? new Uint8Array(),
        );
        const chaincodeAction = peer.ChaincodeAction.deserializeBinary(responsePayload.getExtension_asU8());
        const rwset = ledger.rwset.TxReadWriteSet.deserializeBinary(chaincodeAction.getResults_asU8());
        if (process.env['DEBUG']) {
          console.log(`  [debug]   actionPayload=${action.getPayload_asU8().length}B prp=${(actionPayload.getAction()?.getProposalResponsePayload_asU8() ?? new Uint8Array()).length}B ext=${responsePayload.getExtension_asU8().length}B results=${chaincodeAction.getResults_asU8().length}B ns=${rwset.getNsRwsetList().length} chaincode=${chaincodeAction.getChaincodeId()?.getName() ?? '?'}`);
        }
        for (const ns of rwset.getNsRwsetList()) {
          namespaces.set(ns.getNamespace(), (namespaces.get(ns.getNamespace()) ?? 0) + 1);
          if (ns.getNamespace() !== config.fabric.chaincodeName) continue;
          const kv = ledger.rwset.kvrwset.KVRWSet.deserializeBinary(ns.getRwset_asU8());
          for (const write of kv.getWritesList()) {
            records.push({
              block: number,
              txId,
              valid,
              key: write.getKey(),
              deleted: write.getIsDelete(),
              value: Buffer.from(write.getValue_asU8()).toString('utf8'),
            });
          }
        }
      }
    });
    blockSummary.push(`block ${String(number).padStart(3)}: ${types.join(',') || '(빈 블록)'}`);
  }

  console.log(`channel=${channel} height=${height} blocks=${from}..${to} writes=${records.length}`);
  console.log(`namespaces seen: ${[...namespaces.entries()].map(([n, c]) => `${n}(${c})`).join(' ') || '(없음)'}`);
  console.log(blockSummary.join('\n') + '\n');
  for (const record of records) {
    const flag = record.valid ? ' ' : '!';
    const kind = record.deleted ? 'DEL' : 'PUT';
    console.log(`${flag} block=${String(record.block).padStart(3)} ${kind} ${record.key}  tx=${record.txId.slice(0, 12)}…  ${record.value.slice(0, 120)}`);
  }

  const storageKeys = [...new Set(records.filter((r) => r.valid && r.key.startsWith('DCSTORE:')).map((r) => r.key.slice('DCSTORE:'.length)))];
  const certKeys = [...new Set(records.filter((r) => r.valid && r.key.startsWith('CERT:')).map((r) => r.key.slice('CERT:'.length)))];
  console.log(`\nDCSTORE data_key (${storageKeys.length}): ${storageKeys.join(' ') || '(없음)'}`);
  console.log(`CERT certificateId (${certKeys.length}): ${certKeys.join(' ') || '(없음)'}`);
  console.log('\n! 표시는 무효 판정된 트랜잭션이다. 위 목록의 data_key 로 get_data / data_history 를 부를 수 있다.');
  connection.close();
} finally {
  await tunnel?.close();
}
