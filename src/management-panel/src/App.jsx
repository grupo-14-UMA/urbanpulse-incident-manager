import { useState } from 'react';
import { Link, NavLink, Route, Routes, useLocation } from 'react-router-dom';

import { checkApiConnection, getApiBaseUrl } from './api.js';
import MapPage from './pages/MapPage.jsx';
import PlaceholderPage from './pages/PlaceholderPage.jsx';

const navigation = [
  { label: 'Mapa', to: '/', icon: '⌖', end: true },
  { label: 'Incidencias', to: '/incidents', icon: '▤' },
  { label: 'Detalle', to: '/incidents/demo', icon: '≡' },
];

function pageTitle(pathname) {
  if (pathname.startsWith('/incidents/')) return 'Detalle de incidencia';
  if (pathname.startsWith('/incidents')) return 'Incidencias';
  return 'Mapa de incidencias';
}

export default function App() {
  const location = useLocation();
  const [apiStatus, setApiStatus] = useState({ kind: 'idle', label: 'Comprobar API' });

  async function handleCheckApi() {
    setApiStatus({ kind: 'checking', label: 'Comprobando…' });
    try {
      const response = await checkApiConnection();
      setApiStatus({
        kind: 'reachable',
        label: response.status === 404 ? 'Backend accesible · endpoint pendiente' : `Respuesta HTTP ${response.status}`,
      });
    } catch {
      setApiStatus({ kind: 'error', label: 'Sin conexión o CORS bloqueado' });
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link className="brand" to="/" aria-label="UrbanPulse, inicio">
          <span className="brand-mark" aria-hidden="true">U</span>
          <span className="brand-name">urban<span>pulse</span></span>
        </Link>

        <div className="workspace-label">ESPACIO DE TRABAJO</div>
        <nav className="primary-nav" aria-label="Navegación principal">
          {navigation.map((item) => (
            <NavLink
              className={({ isActive }) => `nav-link${isActive ? ' nav-link-active' : ''}`}
              end={item.end}
              key={item.to}
              to={item.to}
            >
              <span className="nav-icon" aria-hidden="true">{item.icon}</span>
              {item.label}
              {item.label === 'Mapa' && <span className="active-indicator" />}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <span className="city-pin" aria-hidden="true">●</span>
          <div><strong>Málaga</strong><small>Panel municipal</small></div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <p className="eyebrow">OPERACIONES <span>/</span> MÁLAGA</p>
            <h1>{pageTitle(location.pathname)}</h1>
          </div>
          <div className={`api-address api-${apiStatus.kind}`} title="Configura esta URL con VITE_API_URL">
            <span className="api-dot" />
            <span className="api-label">{apiStatus.label}</span>
            <code>{getApiBaseUrl()}</code>
            <button className="api-check" disabled={apiStatus.kind === 'checking'} onClick={handleCheckApi} type="button">
              Probar
            </button>
          </div>
        </header>

        <Routes>
          <Route element={<MapPage />} path="/" />
          <Route
            element={<PlaceholderPage description="Aquí aparecerá el listado filtrable de incidencias." title="Listado de incidencias" />}
            path="/incidents"
          />
          <Route
            element={<PlaceholderPage description="Aquí se consultará y gestionará cada incidencia." title="Detalle de incidencia" />}
            path="/incidents/:incidentId"
          />
          <Route element={<PlaceholderPage description="La página que buscas no existe." title="Página no encontrada" />} path="*" />
        </Routes>
      </main>
    </div>
  );
}
