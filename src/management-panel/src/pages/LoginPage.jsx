import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext.jsx';

export default function LoginPage() {
  const { login, status, restoreError } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate replace to={location.state?.from?.pathname || '/'} />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(email, password);
    } catch (loginError) {
      setError(loginError.message || 'No se pudo iniciar sesión. Inténtalo de nuevo.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-screen">
      <section className="login-card" aria-labelledby="login-title">
        <Link className="brand login-brand" to="/login" aria-label="UrbanPulse, inicio de sesión">
          <span className="brand-mark" aria-hidden="true">U</span>
          <span className="brand-name">urban<span>pulse</span></span>
        </Link>
        <p className="login-kicker">PANEL MUNICIPAL · MÁLAGA</p>
        <h1 id="login-title">Inicia sesión</h1>
        <p className="login-intro">Accede con tu cuenta de Operador para gestionar las incidencias de la ciudad.</p>

        {status === 'restoring' && <p className="login-notice" role="status">Comprobando la sesión guardada…</p>}
        {(error || restoreError) && <p className="login-error" role="alert">{error || restoreError}</p>}

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="login-email">Correo electrónico</label>
          <input
            autoComplete="username"
            autoFocus
            id="login-email"
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />

          <label htmlFor="login-password">Contraseña</label>
          <input
            autoComplete="current-password"
            id="login-password"
            name="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />

          <button className="auth-submit" disabled={submitting || status === 'restoring'} type="submit">
            {submitting ? 'Entrando…' : 'Entrar al panel'}
          </button>
        </form>
        <p className="login-footnote">Solo las cuentas del personal municipal pueden acceder.</p>
      </section>
    </main>
  );
}
