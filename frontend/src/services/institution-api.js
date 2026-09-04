import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listInstitutions({ page = 0, size = 20, sort } = {}) {
  const payload = await apiRequest('/institutions', {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function getInstitution(institutionId) {
  const payload = await apiRequest(`/institutions/${institutionId}`);
  return unwrapData(payload);
}

export async function createInstitution(body) {
  const payload = await apiRequest('/institutions', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateInstitution(institutionId, body) {
  const payload = await apiRequest(`/institutions/${institutionId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}
