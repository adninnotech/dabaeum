import { apiRequest, unwrapData } from '@/services/api-client';

export async function loginLocalAccount({ email, password }) {
  const payload = await apiRequest('/auth/login', {
    method: 'POST',
    token: null,
    body: { email, password },
  });
  return unwrapData(payload);
}

export async function signupLocalAccount({ email, password, name, phone, birthDate }) {
  const payload = await apiRequest('/auth/signup', {
    method: 'POST',
    token: null,
    body: {
      email,
      password,
      name,
      phone: phone || null,
      birthDate: birthDate || null,
    },
  });
  return unwrapData(payload);
}
