import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { ApiError, apiRequest } from '../api.js';
import { clearStoredSession, getStoredSession, storeSession } from '../authStorage.js';

const AuthContext = createContext(null);

function isOperator(auth) {
  return auth?.user?.role === 'OPERATOR';
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  const [status, setStatus] = useState('restoring');
  const [restoreError, setRestoreError] = useState(null);

  const clearSession = useCallback(() => {
    clearStoredSession();
    setSession(null);
    setRestoreError(null);
    setStatus('anonymous');
  }, []);

  const expireSession = useCallback(() => {
    clearStoredSession();
    setSession(null);
    setRestoreError('Tu sesión ha caducado. Vuelve a iniciar sesión.');
    setStatus('anonymous');
  }, []);

  const restoreSession = useCallback(async () => {
    setRestoreError(null);
    const stored = getStoredSession();
    if (!stored?.token) {
      setSession(null);
      setStatus('anonymous');
      return;
    }

    setStatus('restoring');
    try {
      const refreshed = await apiRequest('/api/auth/refresh', { method: 'POST' });
      if (!isOperator(refreshed)) {
        clearStoredSession();
        setSession(null);
        setStatus('anonymous');
        setRestoreError('Tu cuenta no tiene permiso para acceder al panel de gestión.');
        return;
      }
      storeSession(refreshed);
      setSession(refreshed);
      setStatus('authenticated');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearStoredSession();
        setSession(null);
        setStatus('anonymous');
        setRestoreError('Tu sesión ha caducado. Vuelve a iniciar sesión.');
      } else {
        setRestoreError(error.message || 'No se pudo restaurar la sesión. Inténtalo de nuevo.');
        setStatus('restore-error');
      }
    }
  }, []);

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  useEffect(() => {
    const handleSessionExpired = () => expireSession();
    window.addEventListener('urbanpulse:session-expired', handleSessionExpired);
    return () => window.removeEventListener('urbanpulse:session-expired', handleSessionExpired);
  }, [expireSession]);

  const login = useCallback(async (email, password) => {
    const auth = await apiRequest('/api/auth/login', {
      method: 'POST',
      skipAuth: true,
      body: JSON.stringify({ email, password }),
    });

    if (!isOperator(auth)) {
      clearStoredSession();
      setSession(null);
      setStatus('anonymous');
      throw new ApiError('Tu cuenta no tiene permiso para acceder al panel de gestión.', {
        kind: 'forbidden',
        status: 403,
      });
    }

    storeSession(auth);
    setSession(auth);
    setRestoreError(null);
    setStatus('authenticated');
    return auth;
  }, []);

  const value = useMemo(() => ({
    session,
    status,
    restoreError,
    login,
    logout: clearSession,
    retryRestore: restoreSession,
  }), [session, status, restoreError, login, clearSession, restoreSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider.');
  return context;
}
