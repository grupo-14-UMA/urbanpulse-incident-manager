import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  INCIDENT_CATEGORIES,
  INCIDENT_PRIORITIES,
  INCIDENT_STATUSES,
  loadMockIncidentPage,
} from '../mock/incidentList.js';

const PAGE_SIZE = 6;

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

const DATE_FORMAT = new Intl.DateTimeFormat('es-ES', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

const EMPTY_FILTERS = { status: '', category: '', priority: '', dateFrom: '', dateTo: '' };

function formatDate(value) {
  return DATE_FORMAT.format(new Date(value));
}

export default function IncidentListPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [sort, setSort] = useState('date-desc');
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState({ items: [], total: 0, page: 1, pageCount: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const filterKey = useMemo(() => JSON.stringify(filters), [filters]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    loadMockIncidentPage({ filters, page, pageSize: PAGE_SIZE, sort })
      .then((nextResult) => {
        if (active) {
          setResult(nextResult);
          if (nextResult.page !== page) setPage(nextResult.page);
        }
      })
      .catch(() => {
        if (active) setError('No se pudo cargar la lista. Inténtalo de nuevo.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => { active = false; };
  // filterKey avoids reloading when React recreates an equivalent filters object.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey, page, reloadKey, sort]);

  function updateFilter(event) {
    const { name, value } = event.target;
    setFilters((current) => ({ ...current, [name]: value }));
    setPage(1);
  }

  function changeSort(event) {
    setSort(event.target.value);
    setPage(1);
  }

  const firstItem = result.total === 0 ? 0 : (result.page - 1) * PAGE_SIZE + 1;
  const lastItem = Math.min(result.page * PAGE_SIZE, result.total);

  return (
    <section aria-labelledby="incidents-heading" className="incident-list-page">
      <div className="list-heading-row">
        <div>
          <p className="section-kicker">BANDEJA DE TRABAJO</p>
          <h2 id="incidents-heading">Incidencias</h2>
          <p className="list-description">Consulta y filtra las incidencias registradas en Málaga.</p>
        </div>
        <span className="list-sample-badge">Datos simulados</span>
      </div>

      <div aria-label="Filtros de incidencias" className="incident-filters">
        <label>
          Estado
          <select name="status" onChange={updateFilter} value={filters.status}>
            <option value="">Todos</option>
            {INCIDENT_STATUSES.map((status) => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}
          </select>
        </label>
        <label>
          Categoría
          <select name="category" onChange={updateFilter} value={filters.category}>
            <option value="">Todas</option>
            {INCIDENT_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {CATEGORY_LABELS[category] || category}
              </option>
            ))}
          </select>
        </label>
        <label>
          Prioridad
          <select name="priority" onChange={updateFilter} value={filters.priority}>
            <option value="">Todas</option>
            {INCIDENT_PRIORITIES.map((priority) => <option key={priority} value={priority}>{PRIORITY_LABELS[priority]}</option>)}
          </select>
        </label>
        <label>
          Desde
          <input max={filters.dateTo || undefined} name="dateFrom" onChange={updateFilter} type="date" value={filters.dateFrom} />
        </label>
        <label>
          Hasta
          <input min={filters.dateFrom || undefined} name="dateTo" onChange={updateFilter} type="date" value={filters.dateTo} />
        </label>
        <label className="sort-filter">
          Ordenar por
          <select onChange={changeSort} value={sort}>
            <option value="date-desc">Más recientes</option>
            <option value="date-asc">Más antiguas</option>
            <option value="priority-desc">Prioridad más alta</option>
            <option value="priority-asc">Prioridad más baja</option>
          </select>
        </label>
        <button
          className="clear-filters"
          disabled={Object.values(filters).every((value) => !value) && sort === 'date-desc'}
          onClick={() => { setFilters(EMPTY_FILTERS); setSort('date-desc'); setPage(1); }}
          type="button"
        >
          Limpiar filtros
        </button>
      </div>

      <div className="incident-table-card">
        <div className="table-summary" aria-live="polite">
          {loading ? 'Cargando incidencias…' : `${result.total} incidencias · ${firstItem}–${lastItem}`}
        </div>

        {error && (
          <div className="list-error" role="alert">
            <p>{error}</p>
            <button className="auth-secondary" onClick={() => setReloadKey((current) => current + 1)} type="button">Reintentar</button>
          </div>
        )}

        {loading && result.total === 0 ? (
          <div className="list-state" role="status">
            <span aria-hidden="true" className="auth-spinner" />
            <p>Cargando incidencias…</p>
          </div>
        ) : result.total === 0 && !loading && !error ? (
          <div className="list-state" role="status">
            <span aria-hidden="true" className="placeholder-icon">⌕</span>
            <strong>No hay incidencias con esos filtros</strong>
            <p>Prueba a cambiar los filtros o a seleccionar otro rango de fechas.</p>
          </div>
        ) : (
          <div className="incident-table-scroll">
            <table className="incident-table">
              <thead>
                <tr>
                  <th scope="col">Incidencia</th>
                  <th scope="col">Categoría</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Prioridad</th>
                  <th scope="col">Fecha de registro</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {result.items.map((incident) => (
                  <tr
                    className="incident-table-row"
                    key={incident.id}
                  >
                    <td>
                      <Link className="incident-title-link" to={`/incidents/${incident.id}`}>
                        {incident.title}
                      </Link>
                      <small>#{incident.id.toString().padStart(4, '0')}</small>
                    </td>
                    <td>{CATEGORY_LABELS[incident.categoryName] || incident.categoryName || 'Sin categoría'}</td>
                    <td><span className={`incident-status status-${incident.status.toLowerCase()}`}>{STATUS_LABELS[incident.status]}</span></td>
                    <td><span className={`incident-priority priority-${incident.priority.toLowerCase()}`}><i />{PRIORITY_LABELS[incident.priority]}</span></td>
                    <td>{formatDate(incident.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {result.total > 0 && !loading && (
          <nav aria-label="Paginación de incidencias" className="list-pagination">
            <span>Página {result.page} de {result.pageCount}</span>
            <div>
              <button aria-label="Página anterior" disabled={loading || result.page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))} type="button">← Anterior</button>
              <button aria-label="Página siguiente" disabled={loading || result.page >= result.pageCount} onClick={() => setPage((current) => current + 1)} type="button">Siguiente →</button>
            </div>
          </nav>
        )}
      </div>
    </section>
  );
}
