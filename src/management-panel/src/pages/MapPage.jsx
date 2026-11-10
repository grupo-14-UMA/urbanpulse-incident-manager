import { useCallback, useEffect, useRef, useState } from 'react';
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';

import { getIncidentsWithinBounds } from '../api.js';
import 'leaflet/dist/leaflet.css';

const MALAGA = [36.7213, -4.4214];

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

export default function MapPage() {
  const [incidents, setIncidents] = useState([]);
  const [mapTruncated, setMapTruncated] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const requestControllerRef = useRef(null);

  const loadVisibleIncidents = useCallback(async (bounds) => {
    requestControllerRef.current?.abort();
    const controller = new AbortController();
    requestControllerRef.current = controller;

    try {
      const result = await getIncidentsWithinBounds(bounds, { signal: controller.signal });
      if (!controller.signal.aborted) {
        setIncidents(result.incidents);
        setMapTruncated(result.truncated);
        setLoadError(null);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setLoadError(error?.message || 'No se pudieron cargar las incidencias.');
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
          <span className="map-count">
            {incidents.length}{mapTruncated ? '+' : ''} {incidents.length === 1 ? 'incidencia' : 'incidencias'} en el área visible
          </span>
          <span className="map-city"><span aria-hidden="true">⌖</span> Málaga, España</span>
        </div>
      </div>
      <div className="map-frame">
        <MapContainer center={MALAGA} className="city-map" zoom={13} scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ViewportIncidentLoader onBoundsChange={loadVisibleIncidents} />
          {incidents.map((incident) => (
            <CircleMarker
              center={[incident.latitude, incident.longitude]}
              key={incident.id}
              pathOptions={{ color: '#183248', fillColor: '#45c69b', fillOpacity: 1, weight: 2 }}
              radius={9}
            >
              <Popup>
                <article className="incident-popup">
                  <span className="popup-category">{incident.categoryName || 'Sin categoría'}</span>
                  <h3>{incident.title}</h3>
                  <p>{incident.description}</p>
                  <time dateTime={incident.createdAt}>
                    {new Date(incident.createdAt).toLocaleDateString('es-ES', {
                      day: 'numeric',
                      month: 'short',
                      year: 'numeric',
                    })}
                  </time>
                </article>
              </Popup>
            </CircleMarker>
          ))}
        </MapContainer>
        {loadError && <div className="map-load-error" role="alert">{loadError}</div>}
        {!loadError && mapTruncated && (
          <div className="map-limit-notice" role="status">
            Acerca el mapa para ver más incidencias de esta zona.
          </div>
        )}
      </div>
    </section>
  );
}
