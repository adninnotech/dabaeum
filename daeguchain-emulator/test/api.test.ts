import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { buildServer } from '../src/api/server.js';
import { createLogger } from '../src/logger.js';
import { InMemoryStorageGateway } from '../src/fabric/fake-storage.js';
import { ProjectStore } from '../src/emulator/projects.js';
import { address } from '../src/emulator/mitum.js';

const PREFIX = '/daeguchain/v2/mitum/storage';
const HASH = 'b'.repeat(64);
const KEY = 'K1K2K3K4K5K6K7K8';
const VALUE = `1|A|${HASH}|1788393600000`;

async function app(options: { writeEnabled?: boolean; token?: string; apiKey?: string } = {}) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'daeguchain-emulator-'));
  const projects = new ProjectStore(path.join(dir, 'projects.json'), address('owner|test'));
  await projects.load();
  return buildServer({
    storage: new InMemoryStorageGateway(options.writeEnabled ?? true),
    projects,
    emulator: { token: options.token, chain: 'dchain', ownerSeed: 'test' },
    log: createLogger('silent'),
    apiKey: options.apiKey,
    fabricInfo: { channel: 'test', chaincode: 'test', mode: 'in-memory' },
  });
}

async function call(server: Awaited<ReturnType<typeof app>>, endpoint: string, body: Record<string, unknown>) {
  const response = await server.inject({
    method: 'POST',
    url: `${PREFIX}/${endpoint}`,
    payload: { token: 'test-token', chain: 'dchain', ...body },
  });
  return { status: response.statusCode, body: response.json() };
}

async function registerProject(server: Awaited<ReturnType<typeof app>>): Promise<string> {
  const { body } = await call(server, 'regist_project', { operation: '0', project_id: '', project_name: 'testSTRG' });
  assert.equal(body.state, 'OK');
  return body.data.storage.data.project_id as string;
}

test('Regist project 응답이 문서의 contract·storage 구조를 따른다', async () => {
  const server = await app();
  const { status, body } = await call(server, 'regist_project', { operation: '0', project_id: '', project_name: 'testSTRG' });
  assert.equal(status, 200);
  assert.deepEqual(Object.keys(body), ['state', 'rcode', 'msg', 'data', 'cid']);
  assert.equal(body.state, 'OK');
  assert.deepEqual(body.rcode, {});
  assert.match(body.cid, /^[0-9a-f]{64}$/);

  const { data } = body;
  assert.match(data.owner, /^0x[0-9a-f]{40}fca$/);
  assert.match(data.contract.data.privatekey, /^[0-9a-f]{64}fpr$/);
  assert.match(data.contract.data.publickey, /^02[0-9a-f]{64}fpu$/);
  assert.equal(data.contract.data.address, data.storage.response.fact.contract);
  assert.equal(data.contract.response.fact._hint, 'mitum-extension-create-contract-account-operation-fact-v0.0.1');
  assert.equal(data.storage.response.fact._hint, 'mitum-storage-register-model-operation-fact-v0.0.1');
  assert.match(data.storage.data.project_id, /^[A-Z]{20}$/);
  assert.equal(data.storage.data.project_name, 'testSTRG');
  assert.equal(data.storage.receipt.in_state, true);
  assert.deepEqual(Object.keys(data.storage.receipt), ['_hint', 'hash', 'operation', 'height', 'confirmed_at', 'reason', 'in_state', 'index']);

  const list = await call(server, 'projects', {});
  assert.equal(list.body.data.length, 1);
  assert.deepEqual(Object.keys(list.body.data[0]), ['contract', 'data', 'tx', 'issued', 'owner']);
  assert.equal(list.body.data[0].data.project_id, data.storage.data.project_id);
  await server.close();
});

test('Create → Get → Update → History → Delete 가 문서 응답 형식으로 돌아온다', async () => {
  const server = await app();
  const projectId = await registerProject(server);

  const created = await call(server, 'create_data', { project_id: projectId, data_key: KEY, data_value: VALUE });
  assert.equal(created.status, 200);
  assert.equal(created.body.state, 'OK');
  const c = created.body.data;
  assert.deepEqual(Object.keys(c), ['contract', 'data', 'tx', 'issued', 'response', 'receipt']);
  assert.deepEqual(c.data, { data_key: KEY, data_value: VALUE });
  assert.match(c.tx.hash, /^[1-9A-HJ-NP-Za-km-z]{40,50}$/);
  assert.match(c.tx.fact_hash, /^[1-9A-HJ-NP-Za-km-z]{40,50}$/);
  assert.equal(c.response.hash, c.tx.hash);
  assert.equal(c.response.fact.hash, c.tx.fact_hash);
  assert.equal(c.response.fact._hint, 'mitum-storage-create-data-operation-fact-v0.0.1');
  assert.equal(c.response.fact.dataKey, KEY);
  assert.equal(c.response.fact.dataValue, VALUE);
  assert.equal(c.response.fact.currency, 'FACT');
  assert.equal(c.receipt.hash, c.tx.fact_hash);
  assert.equal(c.receipt.in_state, true);
  assert.equal(c.receipt.reason, '');
  assert.equal(typeof c.receipt.height, 'number');

  const got = await call(server, 'get_data', { project_id: projectId, data_key: KEY });
  assert.equal(got.body.state, 'OK');
  assert.deepEqual(Object.keys(got.body.data), ['cont_addr', 'data_key', 'data_value', 'deleted', 'operation']);
  assert.equal(got.body.data.cont_addr, c.contract);
  assert.equal(got.body.data.data_value, VALUE);
  assert.equal(got.body.data.deleted, false);
  assert.equal(got.body.data.operation.fact_hash, c.tx.fact_hash);
  assert.equal(got.body.data.operation.height, c.receipt.height);

  const updatedValue = `1|R|${HASH}|1788397200000`;
  const updated = await call(server, 'update_data', { project_id: projectId, data_key: KEY, data_value: updatedValue });
  assert.equal(updated.body.state, 'OK');
  assert.equal(updated.body.data.response.fact._hint, 'mitum-storage-update-data-operation-fact-v0.0.1');
  assert.equal(updated.body.data.data.data_value, updatedValue);

  const history = await call(server, 'data_history', { project_id: projectId, data_key: KEY, limit: '10', offset: '0', reverse: 'true' });
  assert.equal(history.body.state, 'OK');
  assert.equal(history.body.data.length, 2);
  assert.equal(history.body.data[0].data_value, updatedValue, '최신이 먼저');
  assert.equal(history.body.data[1].data_value, VALUE);
  assert.equal(history.body.data[0].operation.fact_hash, updated.body.data.tx.fact_hash);

  const deleted = await call(server, 'delete_data', { project_id: projectId, data_key: KEY });
  assert.equal(deleted.body.state, 'OK');
  assert.deepEqual(deleted.body.data.data, { data_key: KEY });
  assert.equal(deleted.body.data.response.fact._hint, 'mitum-storage-delete-data-operation-fact-v0.0.1');

  const afterDelete = await call(server, 'get_data', { project_id: projectId, data_key: KEY });
  assert.equal(afterDelete.body.state, 'OK');
  assert.equal(afterDelete.body.data.deleted, true);
  assert.equal(afterDelete.body.data.data_value, '');

  const fullHistory = await call(server, 'data_history', { project_id: projectId, data_key: KEY, reverse: 'true' });
  assert.equal(fullHistory.body.data.length, 3);
  assert.equal(fullHistory.body.data[0].deleted, true);
  await server.close();
});

test('업무 오류는 HTTP 200 에 state=ERROR 로, 인증 오류는 401 로 온다', async () => {
  const server = await app({ token: 'secret' });
  const denied = await server.inject({ method: 'POST', url: `${PREFIX}/projects`, payload: { token: 'wrong', chain: 'dchain' } });
  assert.equal(denied.statusCode, 401);
  assert.equal(denied.json().state, 'ERROR');
  assert.equal(denied.json().rcode.code, 'UNAUTHORIZED');

  const okList = await server.inject({ method: 'POST', url: `${PREFIX}/projects`, payload: { token: 'secret', chain: 'dchain' } });
  assert.equal(okList.statusCode, 200);

  const wrongChain = await server.inject({ method: 'POST', url: `${PREFIX}/projects`, payload: { token: 'secret', chain: 'other' } });
  assert.equal(wrongChain.statusCode, 200);
  assert.equal(wrongChain.json().rcode.code, 'INVALID_CHAIN');

  const noProject = await server.inject({
    method: 'POST',
    url: `${PREFIX}/get_data`,
    payload: { token: 'secret', chain: 'dchain', project_id: 'NOPE', data_key: KEY },
  });
  assert.equal(noProject.statusCode, 200);
  assert.equal(noProject.json().rcode.code, 'PROJECT_NOT_FOUND');
  await server.close();
});

test('없는 키·중복 키·쓰기 미승인·키 형식 위반을 구분한다', async () => {
  const server = await app();
  const projectId = await registerProject(server);

  const missing = await call(server, 'get_data', { project_id: projectId, data_key: KEY });
  assert.equal(missing.body.rcode.code, 'DATA_NOT_FOUND');

  await call(server, 'create_data', { project_id: projectId, data_key: KEY, data_value: VALUE });
  const duplicate = await call(server, 'create_data', { project_id: projectId, data_key: KEY, data_value: VALUE });
  assert.equal(duplicate.body.rcode.code, 'DATA_ALREADY_EXISTS');

  const badKey = await call(server, 'create_data', { project_id: projectId, data_key: 'has/slash', data_value: VALUE });
  assert.equal(badKey.body.rcode.code, 'VALIDATION_FAILED');
  const tooLong = await call(server, 'create_data', { project_id: projectId, data_key: 'A'.repeat(21), data_value: VALUE });
  assert.equal(tooLong.body.rcode.code, 'VALIDATION_FAILED');
  const bigValue = await call(server, 'create_data', { project_id: projectId, data_key: 'OTHERKEY', data_value: 'x'.repeat(201) });
  assert.equal(bigValue.body.rcode.code, 'VALIDATION_FAILED');
  await server.close();

  const readOnly = await app({ writeEnabled: false });
  const roProject = await registerProject(readOnly);
  const blocked = await call(readOnly, 'create_data', { project_id: roProject, data_key: KEY, data_value: VALUE });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.rcode.code, 'WRITE_NOT_APPROVED');
  await readOnly.close();
});

test('프로젝트 삭제 후에는 목록에서 빠지고 데이터 요청은 거부된다', async () => {
  const server = await app();
  const projectId = await registerProject(server);
  const removed = await call(server, 'regist_project', { operation: '1', project_id: projectId });
  assert.equal(removed.body.state, 'OK');
  assert.equal(removed.body.data.storage.response.fact._hint, 'mitum-storage-remove-model-operation-fact-v0.0.1');
  assert.equal((await call(server, 'projects', {})).body.data.length, 0);
  const rejected = await call(server, 'get_data', { project_id: projectId, data_key: KEY });
  assert.equal(rejected.body.rcode.code, 'PROJECT_NOT_FOUND');
  await server.close();
});

test('/health 와 /fabric/* 통과 경로는 그대로 동작한다', async () => {
  const server = await app({ apiKey: 'k' });
  assert.equal((await server.inject({ method: 'GET', url: '/health' })).statusCode, 200);
  assert.equal((await server.inject({ method: 'POST', url: '/fabric/evaluate', payload: { function: 'GetDataHistory', arguments: [KEY] } })).statusCode, 401);
  const evaluated = await server.inject({
    method: 'POST',
    url: '/fabric/evaluate',
    headers: { 'x-api-key': 'k' },
    payload: { function: 'GetDataHistory', arguments: [KEY] },
  });
  assert.equal(evaluated.statusCode, 200);
  assert.deepEqual(evaluated.json().payloadJson, []);
  await server.close();
});
