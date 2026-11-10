import { useState } from 'react';
import { Link, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom';

import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import ProtectedRoute from './auth/ProtectedRoute.jsx';
import { checkApiConnection, getApiBaseUrl } from './api.js';
import IncidentListPage from './pages/IncidentListPage.jsx';
import IncidentDetailPage from './pages/IncidentDetailPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import MapPage from './pages/MapPage.jsx';
import PlaceholderPage from './pages/PlaceholderPage.jsx';

const navigation = [
  { label: 'Mapa', to: '/', icon: '⌖', end: true },
  { label: 'Incidencias', to: '/incidents', icon: '▤' },
];

function pageTitle(pathname) {
  if (pathname.startsWith('/incidents/')) return 'Detalle de incidencia';
  if (pathname.startsWith('/incidents')) return 'Incidencias';
  return 'Mapa de incidencias';
}

function ManagementLayout() {
  const location = useLocation();
  const { session, logout } = useAuth();
  const [apiStatus, setApiStatus] = useState({ kind: 'idle', label: 'Comprobar API' });
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const isIncidentDetail = /^\/incidents\/[^/]+$/.test(location.pathname);

  async function handleCheckApi() {
    setApiStatus({ kind: 'checking', label: 'Comprobando…' });
    try {
      await checkApiConnection();
      setApiStatus({ kind: 'reachable', label: 'API accesible' });
    } catch {
      setApiStatus({ kind: 'error', label: 'No se pudo conectar con la API' });
    }
  }

  return (
    <div className={`app-shell${isIncidentDetail && sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <aside className="sidebar">
        <Link className="brand" to="/" aria-label="UrbanPulse, inicio">
          <span className="brand-mark" aria-hidden="true">U</span>
          <span className="brand-name">urban<span>pulse</span></span>
        </Link>

        <div className="workspace-label">ESPACIO DE TRABAJO</div>
        <nav className="primary-nav" aria-label="Navegación principal">
          {navigation.map((item) => (
            <NavLink
              aria-label={item.label}
              className={({ isActive }) => `nav-link${isActive ? ' nav-link-active' : ''}`}
              end={item.end}
              key={item.to}
              to={item.to}
            >
              <span className="nav-icon" aria-hidden="true">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
              {item.label === 'Mapa' && <span className="active-indicator" />}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <span className="city-pin" aria-hidden="true">●</span>
          <div className="sidebar-user"><strong>{session.user.name}</strong><small>Operador · Málaga</small></div>
          <button aria-label="Cerrar sesión" className="logout-button" onClick={logout} title="Cerrar sesión" type="button">
            ↪
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <p className="eyebrow">OPERACIONES <span>/</span> MÁLAGA</p>
            <h1>{pageTitle(location.pathname)}</h1>
          </div>
          <div className="topbar-actions">
            {isIncidentDetail && (
              <button
                aria-expanded={!sidebarCollapsed}
                className="sidebar-toggle"
                onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
                type="button"
              >
                {sidebarCollapsed ? 'Expandir menú' : 'Reducir menú'}
              </button>
            )}
            <div className={`api-address api-${apiStatus.kind}`} title="Configura esta URL con VITE_API_URL">
              <span className="api-dot" />
              <span className="api-label">{apiStatus.label}</span>
              <code>{getApiBaseUrl()}</code>
              <button className="api-check" disabled={apiStatus.kind === 'checking'} onClick={handleCheckApi} type="button">
                Probar
              </button>
            </div>
          </div>
        </header>

        <Outlet />
      </main>
    </div>
  );
}

function PanelRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<LoginPage />} path="/login" />
        <Route element={<ProtectedRoute />}>
          <Route element={<ManagementLayout />}>
            <Route element={<MapPage />} path="/" />
            <Route element={<IncidentListPage />} path="/incidents" />
            <Route
              element={<IncidentDetailPage />}
              path="/incidents/:incidentId"
            />
            <Route element={<PlaceholderPage description="La página que buscas no existe." title="Página no encontrada" />} path="*" />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  );
}

export default function App() {
  return <PanelRoutes />;
}
