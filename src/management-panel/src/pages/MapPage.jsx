import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import L from 'leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet';

import { getIncidentsWithinBounds } from '../api.js';

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
  alumbrado: 'Alumbrado',
  limpieza: 'Limpieza',
  'mobiliario urbano': 'Mobiliario urbano',
  'tráfico y semáforos': 'Tráfico y semáforos',
  arbolado: 'Arbolado',
  otros: 'Otros',
};

const STATUS_COLORS = {
  REPORTED: '#e09a2d',
  VALIDATED: '#397bd1',
  ASSIGNED: '#7958c8',
  IN_PROGRESS: '#e26d42',
  RESOLVED: '#29a47b',
  REJECTED: '#cf5962',
};

function markerIcon(status = 'REPORTED') {
  const normalizedStatus = STATUS_LABELS[status] ? status : 'REPORTED';

  return L.divIcon({
    className: `incident-marker incident-marker-${normalizedStatus.toLowerCase()}`,
    html: '<span></span>',
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function ViewportIncidentLoader({ onBoundsChange }) {
  const map = useMap();

  useEffect(() => {
    let timeout;

    const scheduleLoad = () => {
      clearTimeout(timeout);
      timeout = setTimeout(() => {
        const bounds = map.getBounds();
        onBoundsChange({
          west: bounds.getWest(),
          south: bounds.getSouth(),
          east: bounds.getEast(),
          north: bounds.getNorth(),
        });
      }, 200);
    };

    scheduleLoad();
    map.on('moveend', scheduleLoad);
    map.on('zoomend', scheduleLoad);

    return () => {
      clearTimeout(timeout);
      map.off('moveend', scheduleLoad);
      map.off('zoomend', scheduleLoad);
    };
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
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [mapTruncated, setMapTruncated] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const requestControllerRef = useRef(null);

  const loadVisibleIncidents = useCallback(async (bounds) => {
    requestControllerRef.current?.abort();

    const controller = new AbortController();
    requestControllerRef.current = controller;

    setLoading(true);
    setLoadError(null);

    try {
      const result = await getIncidentsWithinBounds(bounds, {
        signal: controller.signal,
      });

      if (!controller.signal.aborted) {
        setIncidents(result.incidents);
        setMapTruncated(result.truncated);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setLoadError(error?.message || 'No se pudieron cargar las incidencias.');
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => () => requestControllerRef.current?.abort(), []);

  return (
    <section className="map-page" aria-label="Mapa de Málaga">
      <div className="map-toolbar">
        <div>
          <span className="section-kicker">VISTA GENERAL</span>
          <h2>Incidencias en la ciudad</h2>
        </div>

        <div className="map-badges">
          <span className="map-count" aria-live="polite">
            {loading
              ? 'Actualizando mapa…'
              : `${incidents.length}${mapTruncated ? '+' : ''} ${
                  incidents.length === 1 ? 'incidencia' : 'incidencias'
                } en el área visible`}
          </span>
          <span className="map-city">
            <span aria-hidden="true">⌖</span> Málaga, España
          </span>
        </div>
      </div>

      <div className="map-frame">
        <MapContainer
          center={MALAGA}
          className="city-map"
          zoom={13}
          scrollWheelZoom
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          <ViewportIncidentLoader onBoundsChange={loadVisibleIncidents} />

          <MarkerClusterGroup chunkedLoading showCoverageOnHover={false}>
            {incidents.map((incident) => {
              const status = STATUS_LABELS[incident.status]
                ? incident.status
                : 'REPORTED';

              return (
                <Marker
                  icon={markerIcon(status)}
                  key={incident.id}
                  position={[incident.latitude, incident.longitude]}
                  title={incident.title}
                >
                  <Popup>
                    <article className="incident-popup">
                      <span className="popup-category">
                        {CATEGORY_LABELS[incident.categoryName] ||
                          incident.categoryName ||
                          'Sin categoría'}
                      </span>
                      <h3>{incident.title}</h3>
                      <p>{incident.description}</p>
                      <div className="popup-meta">
                        <span>{STATUS_LABELS[status]}</span>
                        <span>
                          Prioridad{' '}
                          {PRIORITY_LABELS[incident.priority] ||
                            incident.priority ||
                            'Sin asignar'}
                        </span>
                      </div>
                      <time dateTime={incident.createdAt}>
                        {formatDate(incident.createdAt)}
                      </time>
                      <Link
                        className="popup-detail-link"
                        to={`/incidents/${incident.id}`}
                      >
                        Ver detalle
                      </Link>
                    </article>
                  </Popup>
                </Marker>
              );
            })}
          </MarkerClusterGroup>
        </MapContainer>

        {loadError && (
          <div className="map-load-error" role="alert">
            {loadError}
          </div>
        )}

        {!loadError && mapTruncated && (
          <div className="map-limit-notice" role="status">
            Acerca el mapa para ver más incidencias de esta zona.
          </div>
        )}
      </div>

      {!loading && !loadError && incidents.length === 0 && (
        <p className="map-message" role="status">
          No hay incidencias en el área visible.
        </p>
      )}

      <div className="map-legend" aria-label="Leyenda de estados">
        {Object.entries(STATUS_LABELS).map(([key, label]) => (
          <span key={key}>
            <i style={{ '--status-color': STATUS_COLORS[key] }} />
            {label}
          </span>
        ))}
      </div>
    </section>
  );
}