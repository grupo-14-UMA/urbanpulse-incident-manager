import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet';

import 'leaflet/dist/leaflet.css';

const MALAGA = [36.7213, -4.4214];

// These starter fixtures mirror the mobile app's in-memory demo data. They are
// not synchronized with incidents created in the mobile app; that needs the API.
const SAMPLE_INCIDENTS = [
  {
    id: 1,
    title: 'Farola fundida en calle Larios',
    description: 'La farola frente al número 12 lleva varias noches sin encenderse.',
    latitude: 36.7196,
    longitude: -4.4216,
    categoryName: 'alumbrado',
    createdAt: '2026-10-28T19:42:00.000Z',
  },
  {
    id: 2,
    title: 'Contenedor desbordado en El Perchel',
    description: 'El contenedor de la esquina tiene bolsas acumuladas en la acera desde el fin de semana.',
    latitude: 36.7155,
    longitude: -4.4302,
    categoryName: 'limpieza',
    createdAt: '2026-10-30T08:15:00.000Z',
  },
  {
    id: 3,
    title: 'Semáforo apagado en avenida de Andalucía',
    description: 'El semáforo para peatones del cruce con calle Héroe de Sostoa no funciona.',
    latitude: 36.7139,
    longitude: -4.4361,
    categoryName: 'tráfico y semáforos',
    createdAt: '2026-11-01T11:03:00.000Z',
  },
  {
    id: 4,
    title: 'Banco roto en el paseo de La Malagueta',
    description: 'A uno de los bancos del paseo le faltan dos tablones del respaldo.',
    latitude: 36.7193,
    longitude: -4.4082,
    categoryName: null,
    createdAt: '2026-11-02T17:30:00.000Z',
  },
];

export default function MapPage() {
  return (
    <section className="map-page" aria-label="Mapa de Málaga">
      <div className="map-toolbar">
        <div>
          <span className="section-kicker">VISTA GENERAL</span>
          <h2>Incidencias en la ciudad</h2>
        </div>
        <div className="map-badges">
          <span className="sample-count">{SAMPLE_INCIDENTS.length} incidencias de muestra</span>
          <span className="map-city"><span aria-hidden="true">⌖</span> Málaga, España</span>
        </div>
      </div>
      <div className="map-frame">
        <MapContainer center={MALAGA} className="city-map" zoom={13} scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {SAMPLE_INCIDENTS.map((incident) => (
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
      </div>
    </section>
  );
}
