import { MapContainer, TileLayer } from 'react-leaflet';

import 'leaflet/dist/leaflet.css';

const MALAGA = [36.7213, -4.4214];

export default function MapPage() {
  return (
    <section className="map-page" aria-label="Mapa de Málaga">
      <div className="map-toolbar">
        <div>
          <span className="section-kicker">VISTA GENERAL</span>
          <h2>Incidencias en la ciudad</h2>
        </div>
        <span className="map-city"><span aria-hidden="true">⌖</span> Málaga, España</span>
      </div>
      <div className="map-frame">
        <MapContainer center={MALAGA} className="city-map" zoom={13} scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
        </MapContainer>
      </div>
    </section>
  );
}
