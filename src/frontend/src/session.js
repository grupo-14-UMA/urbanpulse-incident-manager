import * as SecureStore from 'expo-secure-store';

const SESSION_KEY = 'urbanpulse.session';
const ROLES = new Set(['CITIZEN', 'TECHNICIAN', 'OPERATOR']);

function normalizeSession(value) {
  if (!value || typeof value !== 'object' || typeof value.token !== 'string' || !value.token) {
    return null;
  }

  const { user } = value;
  if (
    !user ||
    typeof user !== 'object' ||
    !Number.isInteger(user.id) ||
    typeof user.name !== 'string' ||
    typeof user.email !== 'string' ||
    !ROLES.has(user.role)
  ) {
    return null;
  }

  return {
    token: value.token,
    expiresAt: typeof value.expiresAt === 'string' ? value.expiresAt : null,
    user: { id: user.id, name: user.name, email: user.email, role: user.role },
  };
}

export async function readSession() {
  const stored = await SecureStore.getItemAsync(SESSION_KEY);
  if (!stored) {
    return null;
  }

  try {
    const session = normalizeSession(JSON.parse(stored));
    if (!session) {
      await clearSession();
    }
    return session;
  } catch {
    await clearSession();
    return null;
  }
}

export async function saveSession(session) {
  const normalized = normalizeSession(session);
  if (!normalized) {
    throw new Error('La sesión recibida no es válida.');
  }
  await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(normalized));
  return normalized;
}

export async function clearSession() {
  await SecureStore.deleteItemAsync(SESSION_KEY);
}
