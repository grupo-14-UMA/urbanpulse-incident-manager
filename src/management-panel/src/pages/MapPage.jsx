import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import L from 'leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet';

import { loadMockIncidents, MOCK_CATEGORIES, MOCK_STATUSES } from '../mock/incidents.js';

import 'leaflet/dist/leaflet.css';
import 'react-leaflet-cluster/dist/assets/MarkerCluster.css';
import 'react-leaflet-cluster/dist/assets/MarkerCluster.Default.css';

const MALAGA = [36.7213, -4.4214];

const STATUS_LABELS = {
  REPORTED: 'Registrada',
  VALIDATED: 'Validada',
  ASSIGNED: 'Asignada',
  IN_PROGRESS: 'En curso',
  RESOLVED: 'Resuelta',
  REJECTED: 'Rechazada',
};

const PRIORITY_LABELS = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  URGENT: 'Urgente',
};

const CATEGORY_LABELS = {
  'alumbrado': 'Alumbrado',
  'limpieza': 'Limpieza',
  'mobiliario urbano': 'Mobiliario urbano',
  'tráfico y semáforos': 'Tráfico y semáforos',
  'arbolado': 'Arbolado',
  'otros': 'Otros',
};

const STATUS_COLORS = {
  REPORTED: '#e09a2d',
  VALIDATED: '#397bd1',
  ASSIGNED: '#7958c8',
  IN_PROGRESS: '#e26d42',
  RESOLVED: '#29a47b',
  REJECTED: '#cf5962',
};

function markerIcon(status) {
  return L.divIcon({
    className: `incident-marker incident-marker-${status.toLowerCase()}`,
    html: '<span></span>',
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function VisibleBoundsWatcher({ onBoundsChange }) {
  const map = useMap();

  useEffect(() => {
    function updateBounds() {
      const bounds = map.getBounds();
      onBoundsChange({
        north: bounds.getNorth(),
        south: bounds.getSouth(),
        east: bounds.getEast(),
        west: bounds.getWest(),
      });
    }

    updateBounds();
    map.on('moveend', updateBounds);
    return () => map.off('moveend', updateBounds);
  }, [map, onBoundsChange]);

  return null;
}

function formatDate(date) {
  return new Date(date).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export default function MapPage() {
  const [bounds, setBounds] = useState(null);
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const handleBoundsChange = useCallback((nextBounds) => setBounds(nextBounds), []);

  useEffect(() => {
    if (!bounds) return undefined;

    let active = true;
    setLoading(true);
    setError(false);
    loadMockIncidents({ bounds, category, status })
      .then((results) => {
        if (active) setIncidents(results);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => { active = false; };
  }, [bounds, category, status, reloadKey]);

  return (
    <section className="map-page" aria-label="Mapa de Málaga">
      <div className="map-toolbar">
        <div>
          <span className="section-kicker">VISTA GENERAL</span>
          <h2>Incidencias en la ciudad</h2>
        </div>
        <div className="map-badges">
          <span className="sample-count" aria-live="polite">
            {loading ? 'Actualizando mapa…' : `${incidents.length} en el área visible`}
          </span>
          <span className="map-city"><span aria-hidden="true">⌖</span> Málaga, España</span>
        </div>
      </div>

      <div className="map-filters" aria-label="Filtros del mapa">
        <label>
          Categoría
          <select onChange={(event) => setCategory(event.target.value)} value={category}>
            <option value="">Todas</option>
            {MOCK_CATEGORIES.map((item) => <option key={item} value={item}>{CATEGORY_LABELS[item]}</option>)}
          </select>
        </label>
        <label>
          Estado
          <select onChange={(event) => setStatus(event.target.value)} value={status}>
            <option value="">Todos</option>
            {MOCK_STATUSES.map((item) => <option key={item} value={item}>{STATUS_LABELS[item]}</option>)}
          </select>
        </label>
        <span className="mock-data-note"><span aria-hidden="true">●</span> Datos simulados</span>
      </div>

      <div className="map-frame">
        <MapContainer center={MALAGA} className="city-map" zoom={13} scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <VisibleBoundsWatcher onBoundsChange={handleBoundsChange} />
          <MarkerClusterGroup chunkedLoading showCoverageOnHover={false}>
            {incidents.map((incident) => (
              <Marker
                icon={markerIcon(incident.status)}
                key={incident.id}
                position={[incident.latitude, incident.longitude]}
                title={incident.title}
              >
                <Popup>
                  <article className="incident-popup">
                    <span className="popup-category">{CATEGORY_LABELS[incident.categoryName] || 'Sin categoría'}</span>
                    <h3>{incident.title}</h3>
                    <p>{incident.description}</p>
                    <div className="popup-meta">
                      <span>{STATUS_LABELS[incident.status]}</span>
                      <span>Prioridad {PRIORITY_LABELS[incident.priority]}</span>
                    </div>
                    <time dateTime={incident.createdAt}>{formatDate(incident.createdAt)}</time>
                    <Link className="popup-detail-link" to={`/incidents/${incident.id}`}>Ver detalle</Link>
                  </article>
                </Popup>
              </Marker>
            ))}
          </MarkerClusterGroup>
        </MapContainer>
      </div>

      {error && (
        <div className="map-message map-error-message" role="alert">
          <span>No se pudieron cargar las incidencias del área.</span>
          <button onClick={() => setReloadKey((key) => key + 1)} type="button">Reintentar</button>
        </div>
      )}
      {bounds && !loading && !error && incidents.length === 0 && (
        <p className="map-message" role="status">No hay incidencias con estos filtros en el área visible.</p>
      )}

      <div className="map-legend" aria-label="Leyenda de estados">
        {Object.entries(STATUS_LABELS).map(([key, label]) => (
          <span key={key}><i style={{ '--status-color': STATUS_COLORS[key] }} />{label}</span>
        ))}
      </div>
      <p className="mock-data-disclaimer">Datos simulados. La consulta real por área se conectará cuando esté disponible la API (#54).</p>
    </section>
  );
}
