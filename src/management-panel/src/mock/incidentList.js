import {
  getMockIncidents,
  MOCK_CATEGORIES,
  MOCK_PRIORITIES,
  MOCK_STATUSES,
} from './incidents.js';

const MOCK_INCIDENTS = getMockIncidents(18);

export const INCIDENT_CATEGORIES = MOCK_CATEGORIES;
export const INCIDENT_STATUSES = MOCK_STATUSES;
export const INCIDENT_PRIORITIES = MOCK_PRIORITIES;

const PRIORITY_RANK = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };

/**
 * Local stand-in for GET /api/incidents. Replace this boundary with the
 * paginated API from issue #55 when it is ready.
 */
export async function loadMockIncidentPage({ filters, page, pageSize, sort }) {
  await new Promise((resolve) => window.setTimeout(resolve, 120));

  const filtered = MOCK_INCIDENTS.filter((incident) => {
    const createdDate = toLocalDateInputValue(incident.createdAt);
    return (!filters.status || incident.status === filters.status)
      && (!filters.category || incident.categoryName === filters.category)
      && (!filters.priority || incident.priority === filters.priority)
      && (!filters.dateFrom || createdDate >= filters.dateFrom)
      && (!filters.dateTo || createdDate <= filters.dateTo);
  });

  filtered.sort((left, right) => {
    if (sort === 'priority-desc') {
      return PRIORITY_RANK[right.priority] - PRIORITY_RANK[left.priority]
        || right.createdAt.localeCompare(left.createdAt);
    }
    if (sort === 'priority-asc') {
      return PRIORITY_RANK[left.priority] - PRIORITY_RANK[right.priority]
        || right.createdAt.localeCompare(left.createdAt);
    }
    return sort === 'date-asc'
      ? left.createdAt.localeCompare(right.createdAt)
      : right.createdAt.localeCompare(left.createdAt);
  });

  const total = filtered.length;
  const pageCount = Math.ceil(total / pageSize);
  const safePage = Math.min(page, Math.max(pageCount, 1));
  const start = (safePage - 1) * pageSize;

  return {
    items: filtered.slice(start, start + pageSize),
    total,
    page: safePage,
    pageSize,
    pageCount,
  };
}

function toLocalDateInputValue(value) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
