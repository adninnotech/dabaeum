/**
 * 원장에 기록되는 값의 형식. 백엔드 Java 의 StorageValue, 체인코드의
 * storageDataValuePattern(^1\|[ARS]\|[0-9a-f]{64}\|[1-9][0-9]{0,18}$) 과 같다.
 *
 *   {schemaVersion}|{status}|{vcHash}|{epochMillis}
 *   1|A|9f86d0…|1756857600000
 */
export type RegistryStatus = 'ACTIVE' | 'REVOKED' | 'SUPERSEDED';

export interface StorageValue {
  readonly schemaVersion: 1;
  readonly status: RegistryStatus;
  readonly vcHash: string;
  /** 밀리초 정밀도의 UTC 시각 */
  readonly eventTime: Date;
}

export const STORAGE_KEY_PATTERN = /^[A-Z0-9]{16}$/;
const HASH_PATTERN = /^[0-9a-f]{64}$/;
const VALUE_PATTERN = /^1\|[ARS]\|[0-9a-f]{64}\|[1-9][0-9]{0,18}$/;

const STATUS_TO_CODE: Record<RegistryStatus, 'A' | 'R' | 'S'> = {
  ACTIVE: 'A',
  REVOKED: 'R',
  SUPERSEDED: 'S',
};

const CODE_TO_STATUS: Record<string, RegistryStatus> = {
  A: 'ACTIVE',
  R: 'REVOKED',
  S: 'SUPERSEDED',
};

export function isStorageKey(value: string): boolean {
  return STORAGE_KEY_PATTERN.test(value);
}

export function isVcHash(value: string): boolean {
  return HASH_PATTERN.test(value);
}

export function encodeStorageValue(value: StorageValue): string {
  if (value.schemaVersion !== 1) throw new Error('schemaVersion must be 1');
  if (!isVcHash(value.vcHash)) throw new Error('vcHash must be 64 lowercase hex characters');
  const epochMillis = value.eventTime.getTime();
  if (!Number.isInteger(epochMillis) || epochMillis <= 0) throw new Error('eventTime is invalid');
  return `1|${STATUS_TO_CODE[value.status]}|${value.vcHash}|${epochMillis}`;
}

export function decodeStorageValue(encoded: string): StorageValue {
  if (!VALUE_PATTERN.test(encoded)) throw new Error('storage value is not canonical');
  const [, code, vcHash, millis] = encoded.split('|') as [string, string, string, string];
  const status = CODE_TO_STATUS[code];
  if (!status) throw new Error('storage status is not supported');
  const epochMillis = Number(millis);
  if (String(epochMillis) !== millis) throw new Error('epochMillis is not canonical');
  return { schemaVersion: 1, status, vcHash, eventTime: new Date(epochMillis) };
}
