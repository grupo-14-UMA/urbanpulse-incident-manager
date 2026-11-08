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

async function request(path, { method = 'GET', body } = {}) {
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
  try {
    timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
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
    const message = controller.signal.aborted
      ? 'El backend no respondió en ' +
        REQUEST_TIMEOUT_MS / 1000 +
        ' segundos. Comprueba la conexión de red y EXPO_PUBLIC_API_URL.'
      : CONNECTION_ERROR;
    const error = new ApiError(message, { cause });
    logFailure(method, path, undefined, startedAt);
    throw error;
  } finally {
    clearTimeout(timeout);
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

function validateCreatedAt(createdAt) {
  if (typeof createdAt !== 'string' || Number.isNaN(new Date(createdAt).getTime())) {
    throw new ApiError(INVALID_CREATED_AT_ERROR);
  }
}

function expectIncident(incident) {
  if (!incident || typeof incident !== 'object' || Array.isArray(incident)) {
    throw new ApiError('El backend devolvió una incidencia no válida.');
  }

  validateCreatedAt(incident.createdAt);
  return incident;
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

export async function getIncidents() {
  return expectArray(await request('/api/incidents'), 'incidencias').map(expectIncident);
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
