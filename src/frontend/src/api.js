import { API_URL } from './config';

const BASE_URL = typeof API_URL === 'string' ? API_URL.trim().replace(/\/+$/, '') : '';
const REQUEST_TIMEOUT_MS = 10000;
const CONNECTION_ERROR =
  'No se pudo conectar con el backend. Comprueba que está en ejecución y que EXPO_PUBLIC_API_URL es accesible desde el móvil.';
const INVALID_CREATED_AT_ERROR = 'El backend devolvió una fecha de registro no válida.';

class ApiError extends Error {
  constructor(message, { status, payload, cause } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
    this.cause = cause;
  }
}

function logFailure(method, path, status, startedAt) {
  const statusLabel = status ?? 'sin respuesta';
  const durationMs = Date.now() - startedAt;
  console.warn('[api] ' + method + ' ' + path + ' -> ' + statusLabel + ' (' + durationMs + ' ms)');
}

function getResponseErrorMessage(payload, status) {
  const message =
    (typeof payload?.message === 'string' && payload.message) ||
    (typeof payload?.detail === 'string' && payload.detail) ||
    'El backend respondió con el código HTTP ' + status + '.';
  const fieldMessages = Object.values(payload?.errors ?? {}).filter(
    (fieldMessage) => typeof fieldMessage === 'string' && fieldMessage,
  );

  return [message, ...fieldMessages].join(' ');
}

async function request(path, { method = 'GET', body, signal: requestSignal } = {}) {
  if (!BASE_URL) {
    throw new ApiError(
      'Configura EXPO_PUBLIC_API_URL en src/frontend/.env con una dirección del backend accesible desde el móvil.',
    );
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
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: controller.signal,
    });
    responseText = await response.text();
  } catch (cause) {
    if (requestSignal?.aborted && !timedOut) {
      throw cause;
    }
    const message = timedOut
      ? 'El backend no respondió en ' +
        REQUEST_TIMEOUT_MS / 1000 +
        ' segundos. Comprueba la conexión de red y EXPO_PUBLIC_API_URL.'
      : CONNECTION_ERROR;
    const error = new ApiError(message, { cause });
    logFailure(method, path, undefined, startedAt);
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
        const error = new ApiError('El backend devolvió una respuesta que no es JSON válido.', {
          status: response.status,
        });
        logFailure(method, path, response.status, startedAt);
        throw error;
      }
    }
  }

  if (!response.ok) {
    const error = new ApiError(getResponseErrorMessage(payload, response.status), {
      status: response.status,
      payload,
    });
    logFailure(method, path, response.status, startedAt);
    throw error;
  }

  return payload;
}

function normalizeCreatedAt(createdAt) {
  if (typeof createdAt !== 'string') {
    throw new ApiError(INVALID_CREATED_AT_ERROR);
  }

  // Workaround: the backend sends UTC timestamps without a zone. Remove when #85 is done.
  let timestamp = createdAt;
  if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(timestamp)) {
    timestamp += 'Z';
  }
  timestamp = timestamp.replace(/(\.\d{3})\d+(?=Z$)/, '$1');

  const parsedTimestamp = new Date(timestamp);
  if (Number.isNaN(parsedTimestamp.getTime())) {
    throw new ApiError(INVALID_CREATED_AT_ERROR);
  }
  return parsedTimestamp.toISOString();
}

function normalizeIncident(incident) {
  if (!incident || typeof incident !== 'object' || Array.isArray(incident)) {
    throw new ApiError('El backend devolvió una incidencia no válida.');
  }

  return {
    ...incident,
    createdAt: normalizeCreatedAt(incident.createdAt),
  };
}

function expectArray(payload, resource) {
  if (!Array.isArray(payload)) {
    throw new ApiError('El backend devolvió una lista de ' + resource + ' no válida.');
  }
  return payload;
}

export async function getCategories() {
  return expectArray(await request('/api/categories'), 'categorías');
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
    incidents: payload.incidents.map(normalizeIncident),
    truncated: payload.truncated,
  };
}

export async function getIncident(id) {
  const incident = await request('/api/incidents/' + encodeURIComponent(String(id)));
  return normalizeIncident(incident);
}

export async function createIncident({ title, description, latitude, longitude, categoryId }) {
  const body = { title, description, latitude, longitude };
  if (categoryId !== null && categoryId !== undefined) {
    body.categoryId = categoryId;
  }

  const incident = await request('/api/incidents', { method: 'POST', body });
  return normalizeIncident(incident);
}
