import { getStoredSession } from './authStorage.js';

const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(message, { kind = 'unexpected', status = null, errors = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
    this.errors = errors;
  }
}

const USER_MESSAGES = {
  network: 'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.',
  server: 'Algo ha fallado en UrbanPulse. Inténtalo de nuevo en unos minutos.',
  session: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  forbidden: 'No tienes permiso para hacer esto.',
  validation: 'Revisa los datos e inténtalo de nuevo.',
  notFound: 'No se encontró el recurso solicitado.',
  unexpected: 'Algo ha fallado en UrbanPulse. Inténtalo de nuevo en unos minutos.',
};

export function getApiBaseUrl() {
  return API_BASE_URL;
}

function logApiFailure({ method, path, status, kind, hint, startedAt }) {
  const duration = Math.round(performance.now() - startedAt);
  console.warn(
    `[api] ${method} ${path} -> ${status ?? 'sin respuesta'} (${duration} ms) kind=${kind} attempt=1${hint ? ` hint=${hint}` : ''}`,
  );
}

function authExpired() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event('urbanpulse:session-expired'));
  }
}

export async function getIncidentsWithinBounds(bounds, { signal } = {}) {
  const query = new URLSearchParams({
    west: String(bounds.west),
    south: String(bounds.south),
    east: String(bounds.east),
    north: String(bounds.north),
  });
  const payload = await apiRequest(`/api/incidents?${query.toString()}`, { signal });
  if (!payload || !Array.isArray(payload.incidents) || typeof payload.truncated !== 'boolean') {
    throw new Error('La API devolvió un resultado de mapa no válido.');
  }
  return payload;
}

(id, { signal } = {}) {
  return apiRequest(`/api/incidents/${encodeURIComponent(id)}`, { signal });
}

export async function apiRequest(path, options = {}) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const { skipAuth = false, headers: requestHeaders, ...fetchOptions } = options;
  const method = (fetchOptions.method || 'GET').toUpperCase();
  const startedAt = performance.now();
  const session = skipAuth ? null : getStoredSession();
  const headers = {
    Accept: 'application/json',
    ...(fetchOptions.body ? { 'Content-Type': 'application/json' } : {}),
    ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
    ...requestHeaders,
  };

  const timeoutSignal = AbortSignal.timeout(10_000);
  const signal = fetchOptions.signal
    ? AbortSignal.any([fetchOptions.signal, timeoutSignal])
    : timeoutSignal;

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${normalizedPath}`, {
      ...fetchOptions,
      headers,
      signal,
    });
  } catch (error) {
    if (fetchOptions.signal?.aborted && !timeoutSignal.aborted) {
      throw error;
    }
    const hint = timeoutSignal.aborted || error?.name === 'TimeoutError' ? 'timeout' : 'network';
    logApiFailure({ method, path: normalizedPath, status: null, kind: 'network', hint, startedAt });
    throw new ApiError(USER_MESSAGES.network, { kind: 'network' });
  }

  let payload = null;
  if (response.status !== 204) {
    try {
      payload = await response.json();
    } catch {
      if (response.ok) {
        logApiFailure({ method, path: normalizedPath, status: response.status, kind: 'unexpected', hint: 'invalid-json', startedAt });
        throw new ApiError(USER_MESSAGES.unexpected, { kind: 'unexpected', status: response.status });
      }
    }
  }

  if (!response.ok) {
    const isLogin = normalizedPath === '/api/auth/login';
    const isRefresh = normalizedPath === '/api/auth/refresh';
    const kind = response.status === 401 ? (isLogin ? 'credentials' : 'session')
      : response.status === 403 ? 'forbidden'
        : response.status === 400 ? 'validation'
          : response.status >= 500 ? 'server' : 'unexpected';
    logApiFailure({ method, path: normalizedPath, status: response.status, kind, hint: 'http-response', startedAt });
    if (response.status === 401) {
      if (session?.token && !isRefresh) authExpired();
      throw new ApiError(
        isLogin && typeof payload?.message === 'string' ? payload.message : USER_MESSAGES.session,
        { kind: isLogin ? 'credentials' : 'session', status: 401 },
      );
    }
    if (response.status === 403) {
      throw new ApiError(USER_MESSAGES.forbidden, { kind: 'forbidden', status: 403 });
    }
    if (response.status === 400) {
      const fieldMessages = Object.values(payload?.errors || {}).filter((message) => typeof message === 'string');
      const message = typeof payload?.message === 'string'
        ? payload.message
        : fieldMessages.join(' ') || USER_MESSAGES.validation;
      throw new ApiError(message, { kind: 'validation', status: 400, errors: payload?.errors || {} });
    }
    if (response.status === 404) {
      throw new ApiError(USER_MESSAGES.notFound, { kind: 'notFound', status: 404 });
    }
    if (response.status >= 500 || [502, 503, 504].includes(response.status)) {
      throw new ApiError(USER_MESSAGES.server, { kind: 'server', status: response.status });
    }
    throw new ApiError(USER_MESSAGES.unexpected, { kind: 'unexpected', status: response.status });
  }

  return payload;
}

export async function checkApiConnection() {
  await apiRequest('/api/users/me');
  return { status: 200 };
}
