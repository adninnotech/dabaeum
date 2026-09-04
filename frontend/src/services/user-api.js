import { apiRequest, unwrapData } from '@/services/api-client';

export async function fetchAuthSession() {
  const payload = await apiRequest('/auth/session');
  return unwrapData(payload);
}

export async function listUsers({ page = 0, size = 20, sort, status } = {}) {
  const payload = await apiRequest('/users', {
    query: { page, size, sort, status },
  });
  return {
    data: unwrapData(payload) || [],
    page: payload?.page || null,
    meta: payload?.meta || null,
  };
}

export async function getUser(userId) {
  const payload = await apiRequest(`/users/${userId}`);
  return unwrapData(payload);
}

export async function getCurrentUser() {
  const payload = await apiRequest('/users/me');
  return unwrapData(payload);
}

export async function updateCurrentUser(body) {
  const payload = await apiRequest('/users/me', { method: 'PUT', body });
  return unwrapData(payload);
}

export async function createUser(body) {
  const payload = await apiRequest('/users', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateUser(userId, body) {
  const payload = await apiRequest(`/users/${userId}`, { method: 'PUT', body });
  return unwrapData(payload);
}

export async function changeUserStatus(userId, status) {
  // OpenAPI: PATCH, test.http: PUT — PATCH 우선, 실패 시 PUT 재시도
  try {
    const payload = await apiRequest(`/users/${userId}/status`, {
      method: 'PATCH',
      body: { status },
    });
    return unwrapData(payload);
  } catch (error) {
    if (error.status !== 405 && error.status !== 404) throw error;
    const payload = await apiRequest(`/users/${userId}/status`, {
      method: 'PUT',
      body: { status },
    });
    return unwrapData(payload);
  }
}

export async function listUserIdentities(userId) {
  const payload = await apiRequest(`/users/${userId}/identities`);
  return unwrapData(payload) || [];
}

export async function linkUserIdentity(userId, body) {
  const payload = await apiRequest(`/users/${userId}/identities`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function unlinkUserIdentity(userId, identityId) {
  await apiRequest(`/users/${userId}/identities/${identityId}`, {
    method: 'DELETE',
  });
}

export async function listUserRoles(userId) {
  const payload = await apiRequest(`/users/${userId}/roles`);
  return unwrapData(payload) || [];
}

export async function assignUserRole(userId, body) {
  const payload = await apiRequest(`/users/${userId}/roles`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function revokeUserRole(userId, roleId) {
  await apiRequest(`/users/${userId}/roles/${roleId}`, {
    method: 'DELETE',
  });
}
