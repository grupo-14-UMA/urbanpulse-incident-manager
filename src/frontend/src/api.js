import { API_URL } from './config';

const BASE_URL = typeof API_URL === 'string' ? API_URL.trim().replace(/\/+$/, '') : '';
const REQUEST_TIMEOUT_MS = 10000;
const CONNECTION_ERROR =
  'No se pudo conectar con el backend. Comprueba que está en ejecución y que EXPO_PUBLIC_API_URL es accesible desde el móvil.';

class ApiError extends Error {
  constructor(message, status, cause) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.cause = cause;
  }
}

function getResponseErrorMessage(payload, status) {
  const message =
    (typeof payload?.message === 'string' && payload.message) ||
    (typeof payload?.detail === 'string' && payload.detail) ||
    `El backend respondió con el código HTTP ${status}.`;
  const fieldMessages = Object.values(payload?.errors ?? {}).filter(
    (fieldMessage) => typeof fieldMessage === 'string' && fieldMessage,
  );

  return [message, ...fieldMessages].join(' ');
}

async function request(path, { method = 'GET', body } = {}) {
  if (!BASE_URL) {
    throw new ApiError(
      'Configura EXPO_PUBLIC_API_URL en src/frontend/.env con la dirección del backend accesible desde el móvil.',
    );
  }

  let response;
  let responseText;
  const controller = new AbortController();
  let timeout;
  try {
    timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    response = await fetch(`${BASE_URL}${path}`, {
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
    if (controller.signal.aborted) {
      throw new ApiError(
        `El backend no respondió en ${REQUEST_TIMEOUT_MS / 1000} segundos. Comprueba la conexión de red y EXPO_PUBLIC_API_URL.`,
        undefined,
        cause,
      );
    }
    throw new ApiError(CONNECTION_ERROR, undefined, cause);
  } finally {
    clearTimeout(timeout);
  }

  let payload = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      if (response.ok) {
        throw new ApiError('El backend devolvió una respuesta que no es JSON válido.', response.status);
      }
    }
  }

  if (!response.ok) {
    throw new ApiError(getResponseErrorMessage(payload, response.status), response.status, payload);
  }

  return payload;
}

function normalizeCreatedAt(createdAt) {
  if (typeof createdAt !== 'string') {
    throw new ApiError('El backend devolvió una fecha de registro no válida.');
  }

  // The backend stores UTC in a timestamp without a zone, so mark its value as UTC
  // before converting it to ISO format for consistent display on the phone.
  let timestamp = createdAt;
  if (!/(?:Z|[+-]\d{2}:?\d{2})$/i.test(timestamp)) {
    timestamp += 'Z';
  }
  timestamp = timestamp.replace(/(\.\d{3})\d+(?=Z$)/, '$1');

  const parsedTimestamp = new Date(timestamp);
  if (Number.isNaN(parsedTimestamp.getTime())) {
    throw new ApiError('El backend devolvió una fecha de registro no válida.');
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

export async function getCategories() {
  const categories = await request('/api/categories');
  if (!Array.isArray(categories)) {
    throw new ApiError('El backend devolvió una lista de categorías no válida.');
  }
  return categories;
}

export async function getIncidents() {
  const incidents = await request('/api/incidents');
  if (!Array.isArray(incidents)) {
    throw new ApiError('El backend devolvió una lista de incidencias no válida.');
  }
  return incidents.map(normalizeIncident);
}

export async function getIncident(id) {
  const incident = await request(`/api/incidents/${encodeURIComponent(String(id))}`);
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
