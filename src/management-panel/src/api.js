const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

export function getApiBaseUrl() {
  return API_BASE_URL;
}

export async function checkApiConnection() {
  return fetch(`${API_BASE_URL}/api/incidents`, {
    headers: { Accept: 'application/json' },
  });
}

export async function apiRequest(path, options = {}) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const response = await fetch(`${API_BASE_URL}${normalizedPath}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`La API respondió con ${response.status} ${response.statusText}`);
  }

  return response.status === 204 ? null : response.json();
}
