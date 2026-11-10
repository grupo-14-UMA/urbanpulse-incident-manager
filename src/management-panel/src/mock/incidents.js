const CATEGORY_NAMES = [
  'alumbrado',
  'limpieza',
  'mobiliario urbano',
  'tráfico y semáforos',
  'arbolado',
  'otros',
];

const STATUSES = ['REPORTED', 'VALIDATED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

const SAMPLE_INCIDENTS = [
  {
    id: 1,
    title: 'Farola fundida en calle Larios',
    description: 'La farola frente al número 12 lleva varias noches sin encenderse.',
    latitude: 36.7196,
    longitude: -4.4216,
    categoryName: 'alumbrado',
    status: 'REPORTED',
    priority: 'HIGH',
    createdAt: '2026-10-28T19:42:00.000Z',
  },
  {
    id: 2,
    title: 'Contenedor desbordado en El Perchel',
    description: 'El contenedor de la esquina tiene bolsas acumuladas en la acera desde el fin de semana.',
    latitude: 36.7155,
    longitude: -4.4302,
    categoryName: 'limpieza',
    status: 'VALIDATED',
    priority: 'MEDIUM',
    createdAt: '2026-10-30T08:15:00.000Z',
  },
  {
    id: 3,
    title: 'Semáforo apagado en avenida de Andalucía',
    description: 'El semáforo para peatones del cruce con calle Héroe de Sostoa no funciona.',
    latitude: 36.7139,
    longitude: -4.4361,
    categoryName: 'tráfico y semáforos',
    status: 'IN_PROGRESS',
    priority: 'URGENT',
    createdAt: '2026-11-01T11:03:00.000Z',
  },
  {
    id: 4,
    title: 'Banco roto en el paseo de La Malagueta',
    description: 'A uno de los bancos del paseo le faltan dos tablones del respaldo.',
    latitude: 36.7193,
    longitude: -4.4082,
    categoryName: 'mobiliario urbano',
    status: 'RESOLVED',
    priority: 'LOW',
    createdAt: '2026-11-02T17:30:00.000Z',
  },
];

const GENERATED_INCIDENTS = Array.from({ length: 96 }, (_, index) => {
  const categoryName = CATEGORY_NAMES[index % CATEGORY_NAMES.length];
  const status = STATUSES[index % STATUSES.length];
  const day = String(3 + (index % 6)).padStart(2, '0');

  return {
    id: index + SAMPLE_INCIDENTS.length + 1,
    title: `${categoryName[0].toUpperCase()}${categoryName.slice(1)} en zona ${String.fromCharCode(65 + (index % 12))}`,
    description: 'Incidencia simulada para maquetar el mapa del panel mientras se termina la API.',
    latitude: 36.684 + ((index * 37) % 720) / 10000,
    longitude: -4.49 + ((index * 53) % 1280) / 10000,
    categoryName,
    status,
    priority: PRIORITIES[(index * 3) % PRIORITIES.length],
    createdAt: `2026-11-${day}T${String(index % 24).padStart(2, '0')}:15:00.000Z`,
  };
});

const INCIDENTS = [...SAMPLE_INCIDENTS, ...GENERATED_INCIDENTS];

export const MOCK_PRIORITIES = PRIORITIES;

export function getMockIncidents(limit = INCIDENTS.length) {
  return INCIDENTS.slice(0, limit);
}

/**
 * Mock boundary for GET /api/incidents/map. It deliberately accepts visible
 * bounds and filters like the future bounded API request; replace its body
 * with apiRequest when issue #54 is ready.
 */
export async function loadMockIncidents({ bounds, category, status }) {
  await new Promise((resolve) => window.setTimeout(resolve, 120));

  return INCIDENTS.filter((incident) => {
    const inBounds = !bounds || (
      incident.latitude <= bounds.north
      && incident.latitude >= bounds.south
      && incident.longitude <= bounds.east
      && incident.longitude >= bounds.west
    );
    const matchesCategory = !category || incident.categoryName === category;
    const matchesStatus = !status || incident.status === status;

    return inBounds && matchesCategory && matchesStatus;
  });
}

export function getMockIncidentById(id) {
  return INCIDENTS.find((incident) => String(incident.id) === String(id)) || null;
}

export const MOCK_CATEGORIES = CATEGORY_NAMES;
export const MOCK_STATUSES = STATUSES;
