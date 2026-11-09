import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from './AuthContext.jsx';

export default function ProtectedRoute() {
  const { status, restoreError, retryRestore } = useAuth();
  const location = useLocation();

  if (status === 'restoring') {
    return <main className="auth-state" aria-live="polite"><span className="auth-spinner" />Comprobando la sesión…</main>;
  }

  if (status === 'restore-error') {
    return (
      <main className="auth-state">
        <section className="auth-message-card" role="alert">
          <span className="auth-icon" aria-hidden="true">!</span>
          <h1>No se pudo comprobar la sesión</h1>
          <p>{restoreError}</p>
          <button className="auth-submit" onClick={retryRestore} type="button">Reintentar</button>
          <Link className="auth-secondary auth-secondary-link" to="/login">
            Ir al inicio de sesión
          </Link>
        </section>
      </main>
    );
  }

  if (status !== 'authenticated') {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }

  return <Outlet />;
}
