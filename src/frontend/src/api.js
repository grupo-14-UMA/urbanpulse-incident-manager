// Single entry point for every backend call. Screens never call `fetch` directly.
//
// For now this is an in-memory mock of the contract in #13, so the app can be
// built and demoed before the backend exists. #50 replaces it with real HTTP calls
// without changing these function signatures. Everything is forgotten when the
// app reloads.

const MOCK_DELAY_MS = 300;

// The six categories seeded in #12.
const CATEGORIES = [
  { id: 1, name: 'alumbrado' },
  { id: 2, name: 'limpieza' },
  { id: 3, name: 'mobiliario urbano' },
  { id: 4, name: 'tráfico y semáforos' },
  { id: 5, name: 'arbolado' },
  { id: 6, name: 'otros' },
];

// Example incidents so the map is not empty in the demo. Incidents created from
// the form are added to them.
const incidents = [
  {
    id: 1,
    title: 'Farola fundida en calle Larios',
    description: 'La farola frente al número 12 lleva varias noches sin encenderse.',
    latitude: 36.7196,
    longitude: -4.4216,
    categoryId: 1,
    categoryName: 'alumbrado',
    status: 'REPORTED',
    createdAt: '2026-10-28T19:42:00.000Z',
  },
  {
    id: 2,
    title: 'Contenedor desbordado en El Perchel',
    description: 'El contenedor de la esquina tiene bolsas acumuladas en la acera desde el fin de semana.',
    latitude: 36.7155,
    longitude: -4.4302,
    categoryId: 2,
    categoryName: 'limpieza',
    status: 'REPORTED',
    createdAt: '2026-10-30T08:15:00.000Z',
  },
  {
    id: 3,
    title: 'Semáforo apagado en avenida de Andalucía',
    description: 'El semáforo para peatones del cruce con calle Héroe de Sostoa no funciona.',
    latitude: 36.7139,
    longitude: -4.4361,
    categoryId: 4,
    categoryName: 'tráfico y semáforos',
    status: 'REPORTED',
    createdAt: '2026-11-01T11:03:00.000Z',
  },
  {
    id: 4,
    title: 'Banco roto en el paseo de La Malagueta',
    description: 'A uno de los bancos del paseo le faltan dos tablones del respaldo.',
    latitude: 36.7193,
    longitude: -4.4082,
    categoryId: null,
    categoryName: null,
    status: 'REPORTED',
    createdAt: '2026-11-02T17:30:00.000Z',
  },
];
let nextIncidentId = incidents.length + 1;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getCategories() {
  return CATEGORIES.map((category) => ({ ...category }));
}

export async function getIncidents() {
  await delay(MOCK_DELAY_MS);
  return incidents.map((incident) => ({ ...incident }));
}

// Rejects with an error whose `status` is 404 when no incident has that id, the
// same shape the real `GET /api/incidents/{id}` will give.
export async function getIncident(id) {
  await delay(MOCK_DELAY_MS);
  const incident = incidents.find((i) => String(i.id) === String(id));
  if (!incident) {
    const error = new Error(`Incident ${id} not found`);
    error.status = 404;
    throw error;
  }
  return { ...incident };
}

export async function createIncident({ title, description, latitude, longitude, categoryId }) {
  await delay(MOCK_DELAY_MS);

  const category = CATEGORIES.find((c) => c.id === categoryId) ?? null;
  const incident = {
    id: nextIncidentId++,
    title,
    description,
    latitude,
    longitude,
    categoryId: category ? category.id : null,
    categoryName: category ? category.name : null,
    status: 'REPORTED',
    createdAt: new Date().toISOString(),
  };
  incidents.push(incident);

  return { ...incident };
}
