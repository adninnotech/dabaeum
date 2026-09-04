import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import * as grpc from '@grpc/grpc-js';
import {
  connect,
  signers,
  type Contract,
  type Gateway,
  type Identity,
  type Network,
} from '@hyperledger/fabric-gateway';
import type { FabricConfig } from '../config.js';
import type { Logger } from '../logger.js';
import { FabricGatewayError } from './errors.js';

/** 백엔드 Java FabricGatewayFactory 가 여는 것과 같은 구성의 Gateway 연결. */
export interface FabricConnection {
  readonly gateway: Gateway;
  readonly network: Network;
  readonly contract: Contract;
  /** 시스템 체인코드. 블록 높이 조회에만 쓴다. */
  readonly qscc: Contract;
  close(): void;
}

export async function openFabricConnection(config: FabricConfig, log: Logger): Promise<FabricConnection> {
  let client: grpc.Client | undefined;
  try {
    const [certificate, privateKeyPem, tlsRootCert] = await Promise.all([
      fs.readFile(config.certificatePath),
      fs.readFile(config.privateKeyPath),
      fs.readFile(config.tlsCaPath),
    ]);

    // 터널 또는 같은 장비의 peer 로 127.0.0.1 에 붙고, TLS 검증용 이름만 peer 의 것으로 바꾼다.
    client = new grpc.Client(`127.0.0.1:${config.localPort}`, grpc.credentials.createSsl(tlsRootCert), {
      'grpc.ssl_target_name_override': config.overrideAuthority,
      'grpc.default_authority': config.overrideAuthority,
    });

    const identity: Identity = { mspId: config.mspId, credentials: certificate };
    const signer = signers.newPrivateKeySigner(crypto.createPrivateKey(privateKeyPem));

    const gateway = connect({
      client,
      identity,
      signer,
      evaluateOptions: () => ({ deadline: Date.now() + config.evaluateTimeoutMs }),
      endorseOptions: () => ({ deadline: Date.now() + config.endorseTimeoutMs }),
      submitOptions: () => ({ deadline: Date.now() + config.submitTimeoutMs }),
      commitStatusOptions: () => ({ deadline: Date.now() + config.commitTimeoutMs }),
    });
    const network = gateway.getNetwork(config.channelName);
    const contract = network.getContract(config.chaincodeName);
    const qscc = network.getContract('qscc');

    log.info(
      { channel: config.channelName, chaincode: config.chaincodeName, mspId: config.mspId },
      'Fabric Gateway 를 열었다',
    );
    const grpcClient = client;
    return {
      gateway,
      network,
      contract,
      qscc,
      close() {
        gateway.close();
        grpcClient.close();
      },
    };
  } catch (error) {
    client?.close();
    // 인증 자료 경로나 SDK 메시지가 호출자에게 새지 않도록 원인은 로그에만 남긴다.
    log.error({ err: error }, 'Fabric Gateway 연결 실패. 인증 자료, TLS, gRPC 중 한 단계다');
    throw new FabricGatewayError('FABRIC_GATEWAY_CONNECTION_FAILED');
  }
}
