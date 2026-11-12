import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import { getCategories } from '../api.js'; import { getIncidentPage, loadIncidentList } from '../incidentListData.js';

const PAGE_SIZE = 6;

const STATUS_LABELS = {
  REPORTED: 'Registrada',
  VALIDATED: 'Validada',
  ASSIGNED: 'Asignada',
  IN_PROGRESS: 'En curso',
  RESOLVED: 'Resuelta',
  REJECTED: 'Rechazada',
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

const EMPTY_FILTERS = { status: '', category: '', dateFrom: '', dateTo: '' };

function formatDate(value) {
  return DATE_FORMAT.format(new Date(value));
}

export default function IncidentListPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [sort, setSort] = useState('date-desc');
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [incidents, setIncidents] = useState([]); const [categoryOptions, setCategoryOptions] = useState([]); const [categoryError, setCategoryError] = useState('');
  const [truncated, setTruncated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');

    loadIncidentList({ signal: controller.signal })
      .then((response) => {
        setIncidents(response.incidents);
        setTruncated(response.truncated);
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setError(requestError?.message || 'No se pudo cargar la lista. Inténtalo de nuevo.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [reloadKey]);

  useEffect(() => { const controller = new AbortController(); getCategories({ signal: controller.signal }).then(setCategoryOptions).catch((requestError) => { if (!controller.signal.aborted) setCategoryError(requestError?.message || 'No se pudo cargar el catálogo de categorías.'); }); return () => controller.abort(); }, []);
  const statuses = useMemo(() => {
    const knownStatuses = Object.keys(STATUS_LABELS);
    const additionalStatuses = [...new Set(incidents.map((incident) => incident.status))]
      .filter((status) => status && !knownStatuses.includes(status))
      .sort();
    return [...knownStatuses, ...additionalStatuses];
  }, [incidents]);
  const categories = useMemo(
    () => [...categoryOptions].sort((left, right) => left.name.localeCompare(right.name, 'es')),
    [categoryOptions],
  );
  const result = useMemo(() => getIncidentPage({
    incidents,
    filters,
    page,
    pageSize: PAGE_SIZE,
    sort,
  }), [incidents, filters, page, sort]);

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
        <span className="list-source-note">Datos del backend · máximo 500</span>
      </div>

      <p className="list-limit-note" role="note">{categoryError && <span role="alert">{categoryError} </span>}
        {truncated
          ? 'La API limita los resultados a 500 incidencias abiertas en Málaga. El filtro muestra todos los estados, pero este endpoint no devuelve incidencias Rechazadas ni Resueltas; los filtros restantes y la paginación se calculan en el navegador.'
          : 'El endpoint actual solo permite consultar incidencias abiertas en Málaga. El filtro muestra todos los estados, pero no habrá resultados para Rechazada ni Resuelta con este endpoint. Los filtros restantes y la paginación se calculan en el navegador.'}
      </p>

      <div aria-label="Filtros de incidencias" className="incident-filters">
        <label>
          Estado
          <select name="status" onChange={updateFilter} value={filters.status}>
            <option value="">Todos</option>
            {statuses.map((status) => <option key={status} value={status}>{STATUS_LABELS[status] || status}</option>)}
          </select>
        </label>
        <label>
          Categoría
          <select name="category" onChange={updateFilter} value={filters.category}>
            <option value="">Todas</option>
            {categories.map((category) => (
              <option key={category.id} value={category.name}>
                {CATEGORY_LABELS[category.name.toLowerCase()] || category.name}
              </option>
            ))}
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
                  <th scope="col"><span className="visually-hidden">Acciones</span></th>
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
                    <td><span className={`incident-status status-${incident.status.toLowerCase()}`}>{STATUS_LABELS[incident.status] || incident.status}</span></td>
                    <td aria-label="No disponible en la API actual">—</td>
                    <td>{formatDate(incident.createdAt)}</td>
                    <td>
                      <Link className="incident-detail-button" to={`/incidents/${incident.id}`}>
                        Ver detalle
                      </Link>
                    </td>
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
