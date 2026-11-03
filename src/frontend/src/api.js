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

const incidents = [];
let nextIncidentId = 1;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getCategories() {
  return CATEGORIES.map((category) => ({ ...category }));
}

export async function getIncidents() {
  return incidents.map((incident) => ({ ...incident }));
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
