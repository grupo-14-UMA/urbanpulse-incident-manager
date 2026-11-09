const SESSION_KEY = 'urbanpulse.management.session';

export function getStoredSession() {
  if (typeof window === 'undefined') return null;

  try {
    const session = window.localStorage.getItem(SESSION_KEY);
    if (!session) return null;
    const parsed = JSON.parse(session);
    if (typeof parsed?.token !== 'string' || !parsed.token) {
      clearStoredSession();
      return null;
    }
    return parsed;
  } catch {
    clearStoredSession();
    return null;
  }
}

export function storeSession(session) {
  try {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } catch {
    throw new Error('No se pudo guardar la sesión en este navegador.');
  }
}

export function clearStoredSession() {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(SESSION_KEY);
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}
