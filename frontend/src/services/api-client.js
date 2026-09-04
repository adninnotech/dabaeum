import { LocalStorage } from 'quasar';
import { API_BASE_URL, API_BEARER_TOKEN } from '@/config/api';
import { AUTH_STORAGE_KEY } from '@/config/auth';

function getToken() {
  const saved = LocalStorage.getItem(AUTH_STORAGE_KEY);
  if (saved && typeof saved === 'object' && saved.token) {
    return saved.token;
  }
  return API_BEARER_TOKEN;
}

function buildUrl(path, query) {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  const base = API_BASE_URL.startsWith('http')
    ? API_BASE_URL
    : `${typeof window !== 'undefined' ? window.location.origin : 'http://localhost'}${API_BASE_URL}`;
  const url = new URL(`${base.replace(/\/$/, '')}${normalized}`);
  if (query && typeof query === 'object') {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export function createIdempotencyKey(prefix = 'fe') {
  const rand = Math.random().toString(36).slice(2, 10);
  return `${prefix}-${Date.now()}-${rand}`;
}

export async function apiRequest(path, options = {}) {
  const { method = 'GET', query, body, headers = {}, token, idempotencyKey } = options;
  const skipAuth = token === null;
  const authToken = skipAuth ? null : token || getToken();

  const response = await fetch(buildUrl(path, query), {
    method,
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const payload = await parseBody(response);

  if (!response.ok) {
    const message =
      (payload && (payload.message || payload.error || payload.title)) ||
      `API 오류 (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    error.payload = payload;
    throw error;
  }

  return payload;
}

export function unwrapData(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

export function asPage(payload) {
  return {
    data: unwrapData(payload) || [],
    page: payload?.page || null,
    meta: payload?.meta || null,
  };
}
