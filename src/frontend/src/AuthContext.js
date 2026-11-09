import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { refresh, setAccessToken, setUnauthorizedHandler } from './api';
import { clearSession, readSession, saveSession } from './session';

const AuthContext = createContext(null);
const EXPIRED_SESSION_MESSAGE = 'Tu sesión ha caducado. Vuelve a iniciar sesión.';

export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'restoring', session: null, notice: null });

  const signOut = useCallback(async () => {
    setAccessToken(null);
    await clearSession();
    setState({ status: 'signedOut', session: null, notice: null });
  }, []);

  const expireSession = useCallback(async () => {
    setAccessToken(null);
    await clearSession();
    setState({ status: 'signedOut', session: null, notice: EXPIRED_SESSION_MESSAGE });
  }, []);

  const completeAuthentication = useCallback(async (response) => {
    const session = await saveSession(response);
    setAccessToken(session.token);
    setState({ status: 'signedIn', session, notice: null });
  }, []);

  useEffect(() => {
    let active = true;
    setUnauthorizedHandler(expireSession);

    async function restore() {
      try {
        const storedSession = await readSession();
        if (!storedSession) {
          if (active) {
            setState({ status: 'signedOut', session: null, notice: null });
          }
          return;
        }

        setAccessToken(storedSession.token);
        const refreshed = await refresh();
        const session = await saveSession(refreshed);
        if (active) {
          setAccessToken(session.token);
          setState({ status: 'signedIn', session, notice: null });
        }
      } catch {
        if (active) {
          await expireSession();
        }
      }
    }

    restore();
    return () => {
      active = false;
      setUnauthorizedHandler(null);
    };
  }, [expireSession]);

  const value = useMemo(
    () => ({ ...state, completeAuthentication, signOut, clearNotice: () => setState((current) => ({ ...current, notice: null })) }),
    [state, completeAuthentication, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth debe usarse dentro de AuthProvider.');
  }
  return value;
}
