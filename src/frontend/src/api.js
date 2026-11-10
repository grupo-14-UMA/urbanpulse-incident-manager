import { API_URL } from './config';

const BASE_URL = typeof API_URL === 'string' ? API_URL.trim().replace(/\/+$/, '') : '';
const REQUEST_TIMEOUT_MS = 10000;
const RETRY_ATTEMPTS = 3;
const RETRY_DELAYS_MS = [1000, 3000];
const RETRY_BUDGET_MS = 15000;
const CONNECTION_ERROR =
  'No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.';
const SERVER_ERROR = 'Algo ha fallado en UrbanPulse. Inténtalo de nuevo en unos minutos.';
const INVALID_CREATED_AT_ERROR = SERVER_ERROR;
let accessToken = null;
let unauthorizedHandler = null;

class ApiError extends Error {
  constructor(message, { kind = 'unexpected', status, payload, cause } = {}) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
    this.payload = payload;
    this.cause = cause;
  }
}

function logFailure(method, path, status, startedAt, kind, attempt) {
  const statusLabel = status ?? 'sin respuesta';
  const durationMs = Date.now() - startedAt;
  console.warn('[api] ' + method + ' ' + path + ' -> ' + statusLabel + ' (' + durationMs + ' ms, ' + kind + ', intento ' + attempt + ')');
}

function getResponseErrorMessage(payload, status) {
  const message =
    (typeof payload?.message === 'string' && payload.message) ||
    (typeof payload?.detail === 'string' && payload.detail) ||
    '';
  const fieldMessages = Object.values(payload?.errors ?? {}).filter(
    (fieldMessage) => typeof fieldMessage === 'string' && fieldMessage,
  );

  return [message, ...fieldMessages].join(' ');
}

function errorForResponse(payload, status, preserveUnauthorizedMessage) {
  if (status === 401) {
    if (preserveUnauthorizedMessage) {
      return new ApiError(getResponseErrorMessage(payload, status), { kind: 'unauthorized', status, payload });
    }
    return new ApiError('Tu sesión ha caducado. Vuelve a iniciar sesión.', { kind: 'unauthorized', status, payload });
  }
  if (status === 403) {
    return new ApiError('No tienes permiso para hacer esto.', { kind: 'forbidden', status, payload });
  }
  if (status >= 500) {
    return new ApiError(SERVER_ERROR, { kind: 'server', status, payload });
  }
  if (status === 404) {
    return new ApiError(getResponseErrorMessage(payload, status) || 'Esta incidencia no existe.', { kind: 'notFound', status, payload });
  }
  return new ApiError(getResponseErrorMessage(payload, status) || 'Revisa los datos e inténtalo de nuevo.', {
    kind: 'invalid',
    status,
    payload,
  });
}

function isRetryable(error) {
  return error?.kind === 'connection' || (error?.kind === 'server' && [502, 503, 504].includes(error.status));
}

function retryDelay(attempt) {
  const baseDelay = RETRY_DELAYS_MS[attempt - 1];
  return baseDelay * (0.5 + Math.random());
}

export function setAccessToken(token) {
  accessToken = typeof token === 'string' && token ? token : null;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null;
}

async function requestOnce(
  path,
  {
    method = 'GET',
    body,
    signal: requestSignal,
    authenticated = true,
    handleUnauthorized = true,
    preserveUnauthorizedMessage = false,
  } = {},
  attempt,
) {
  if (!BASE_URL) {
    const error = new ApiError(CONNECTION_ERROR, { kind: 'connection' });
    logFailure(method, path, undefined, Date.now(), error.kind, attempt);
    throw error;
  }

  const startedAt = Date.now();
  let response;
  let responseText;
  const controller = new AbortController();
  let timeout;
  let timedOut = false;
  const abortFromRequest = () => controller.abort();
  try {
    if (requestSignal?.aborted) {
      controller.abort();
    } else {
      requestSignal?.addEventListener('abort', abortFromRequest, { once: true });
    }
    timeout = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, REQUEST_TIMEOUT_MS);
    response = await fetch(BASE_URL + path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(authenticated && accessToken ? { Authorization: 'Bearer ' + accessToken } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: controller.signal,
    });
    responseText = await response.text();
  } catch (cause) {
    if (requestSignal?.aborted && !timedOut) {
      throw cause;
    }
    const error = new ApiError(CONNECTION_ERROR, { kind: 'connection', cause });
    logFailure(method, path, undefined, startedAt, 'connection', attempt);
    throw error;
  } finally {
    clearTimeout(timeout);
    requestSignal?.removeEventListener('abort', abortFromRequest);
  }

  let payload = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      if (response.ok) {
        const error = new ApiError(SERVER_ERROR, {
          status: response.status,
        });
        logFailure(method, path, response.status, startedAt, 'unexpected', attempt);
        throw error;
      }
    }
  }

  if (!response.ok) {
    const error = errorForResponse(payload, response.status, preserveUnauthorizedMessage);
    logFailure(method, path, response.status, startedAt, error.kind, attempt);
    if (response.status === 401 && handleUnauthorized && unauthorizedHandler) {
      void unauthorizedHandler();
    }
    throw error;
  }

  return payload;
}

async function request(path, options = {}) {
  const method = options.method ?? 'GET';
  if (!BASE_URL) {
    return requestOnce(path, options, 1);
  }
  const startedAt = Date.now();
  let attempt = 1;
  while (true) {
    try {
      return await requestOnce(path, options, attempt);
    } catch (error) {
      const canRetry = method === 'GET' && attempt < RETRY_ATTEMPTS && isRetryable(error);
      const delay = canRetry ? retryDelay(attempt) : 0;
      if (!canRetry || options.signal?.aborted || Date.now() - startedAt + delay >= RETRY_BUDGET_MS) {
        throw error;
      }
      await new Promise((resolve) => setTimeout(resolve, delay));
      attempt += 1;
    }
  }
}

export async function register({ name, email, password }) {
  return request('/api/auth/register', { method: 'POST', body: { name, email, password }, authenticated: false, handleUnauthorized: false });
}

export async function login({ email, password }) {
  return request('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    authenticated: false,
    handleUnauthorized: false,
    preserveUnauthorizedMessage: true,
  });
}

export async function refresh() {
  return request('/api/auth/refresh', { method: 'POST' });
}

function validateCreatedAt(createdAt) {
  if (typeof createdAt !== 'string' || Number.isNaN(new Date(createdAt).getTime())) {
    throw new ApiError(INVALID_CREATED_AT_ERROR, { kind: 'unexpected' });
  }
}

function expectIncident(incident) {
  if (!incident || typeof incident !== 'object' || Array.isArray(incident)) {
    throw new ApiError(SERVER_ERROR, { kind: 'unexpected' });
  }

  validateCreatedAt(incident.createdAt);
  return incident;
}

function expectArray(payload) {
  if (!Array.isArray(payload)) {
    throw new ApiError(SERVER_ERROR, { kind: 'unexpected' });
  }
  return payload;
}

export async function getCategories() {
  return expectArray(await request('/api/categories'));
}

export async function getIncidentsWithinBounds(bounds, { signal } = {}) {
  const query = [
    ['west', bounds.west],
    ['south', bounds.south],
    ['east', bounds.east],
    ['north', bounds.north],
  ]
    .map(([name, value]) => `${name}=${encodeURIComponent(String(value))}`)
    .join('&');
  const payload = await request(`/api/incidents?${query}`, { signal });
  if (!payload || !Array.isArray(payload.incidents) || typeof payload.truncated !== 'boolean') {
    throw new ApiError('El backend devolvió un resultado de mapa no válido.');
  }
  return {
    incidents: payload.incidents.map(expectIncident),
    truncated: payload.truncated,
  };
}

export async function getIncident(id) {
  const incident = await request('/api/incidents/' + encodeURIComponent(String(id)));
  return expectIncident(incident);
}

export async function createIncident({ title, description, latitude, longitude, categoryId }) {
  const body = { title, description, latitude, longitude };
  if (categoryId !== null && categoryId !== undefined) {
    body.categoryId = categoryId;
  }

  const incident = await request('/api/incidents', { method: 'POST', body });
  return expectIncident(incident);
}
