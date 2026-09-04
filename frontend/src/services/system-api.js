import { apiRequest, unwrapData } from '@/services/api-client';

export async function pingSystem() {
  const payload = await apiRequest('/system/ping', { token: null });
  return unwrapData(payload) ?? payload;
}
