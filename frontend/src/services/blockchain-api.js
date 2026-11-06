import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function getBlockchainMetrics() {
  const payload = await apiRequest('/blockchain/metrics');
  return unwrapData(payload);
}

export async function listBlockchainTransactions({ page = 0, size = 20 } = {}) {
  const payload = await apiRequest('/blockchain/transactions', {
    query: { page, size },
  });
  return asPage(payload);
}

export async function listBlockchainAlerts({ page = 0, size = 20 } = {}) {
  const payload = await apiRequest('/blockchain/alerts', {
    query: { page, size },
  });
  return asPage(payload);
}
