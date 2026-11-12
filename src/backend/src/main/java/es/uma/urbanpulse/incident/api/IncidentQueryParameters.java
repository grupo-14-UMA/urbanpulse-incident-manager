package es.uma.urbanpulse.incident.api;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import es.uma.urbanpulse.incident.IncidentPriority;
import es.uma.urbanpulse.incident.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Shared query parameters for the paginated incident list and bounded map query. */
@Schema(description = "Filtros, paginación y límites opcionales del mapa.")
public class IncidentQueryParameters {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	@Schema(description = "Longitud oeste WGS 84. Si se indica, deben indicarse los cuatro límites.", example = "-4.45")
	private Double west;
	@Schema(description = "Latitud sur WGS 84.", example = "36.68")
	private Double south;
	@Schema(description = "Longitud este WGS 84.", example = "-4.38")
	private Double east;
	@Schema(description = "Latitud norte WGS 84.", example = "36.77")
	private Double north;

	@Min(value = 1, message = "El identificador de categoría debe ser positivo.")
	private Integer categoryId;
	private boolean uncategorized;
	private IncidentStatus status;
	private IncidentPriority priority;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private Instant createdAfter;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private Instant createdBefore;

	@Min(value = 0, message = "La página debe ser cero o mayor.")
	private int page = 0;
	@Min(value = 1, message = "El tamaño de página debe ser positivo.")
	@Max(value = MAX_PAGE_SIZE, message = "El tamaño máximo de página es 100.")
	private int size = DEFAULT_PAGE_SIZE;
	@Pattern(regexp = "createdAt|priority", message = "Ordena por createdAt o priority.")
	private String sortBy = "createdAt";
	@Pattern(regexp = "asc|desc", message = "La dirección debe ser asc o desc.")
	private String sortDirection = "desc";

	public boolean hasBounds() {
		return west != null || south != null || east != null || north != null;
	}

	public IncidentBounds toBounds() {
		return new IncidentBounds(west, south, east, north);
	}

	@AssertTrue(message = "Indica los cuatro límites del mapa y forma un rectángulo WGS 84 válido.")
	@Schema(hidden = true)
	public boolean isBoundsValid() {
		if (!hasBounds()) {
			return true;
		}
		return west != null && south != null && east != null && north != null
				&& Double.isFinite(west) && Double.isFinite(south)
				&& Double.isFinite(east) && Double.isFinite(north)
				&& west >= -180 && west <= 180 && east >= -180 && east <= 180
				&& south >= -90 && south <= 90 && north >= -90 && north <= 90
				&& west < east && south < north;
	}

	@AssertTrue(message = "La fecha inicial debe ser anterior a la fecha final.")
	@Schema(hidden = true)
	public boolean isDateRangeValid() {
		return createdAfter == null || createdBefore == null || createdAfter.isBefore(createdBefore);
	}

	@AssertTrue(message = "Elige categoría o incidencias sin categoría, no ambas opciones.")
	@Schema(hidden = true)
	public boolean isCategoryFilterValid() {
		return categoryId == null || !uncategorized;
	}

	@AssertTrue(message = "La página solicitada es demasiado grande.")
	@Schema(hidden = true)
	public boolean isOffsetValid() {
		return size > 0 && page <= Integer.MAX_VALUE / size;
	}

	public Double getWest() { return west; }
	public void setWest(Double west) { this.west = west; }
	public Double getSouth() { return south; }
	public void setSouth(Double south) { this.south = south; }
	public Double getEast() { return east; }
	public void setEast(Double east) { this.east = east; }
	public Double getNorth() { return north; }
	public void setNorth(Double north) { this.north = north; }
	public Integer getCategoryId() { return categoryId; }
	public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
	public boolean isUncategorized() { return uncategorized; }
	public void setUncategorized(boolean uncategorized) { this.uncategorized = uncategorized; }
	public IncidentStatus getStatus() { return status; }
	public void setStatus(IncidentStatus status) { this.status = status; }
	public IncidentPriority getPriority() { return priority; }
	public void setPriority(IncidentPriority priority) { this.priority = priority; }
	public Instant getCreatedAfter() { return createdAfter; }
	public void setCreatedAfter(Instant createdAfter) { this.createdAfter = createdAfter; }
	public Instant getCreatedBefore() { return createdBefore; }
	public void setCreatedBefore(Instant createdBefore) { this.createdBefore = createdBefore; }
	public int getPage() { return page; }
	public void setPage(int page) { this.page = page; }
	public int getSize() { return size; }
	public void setSize(int size) { this.size = size; }
	public String getSortBy() { return sortBy; }
	public void setSortBy(String sortBy) { this.sortBy = sortBy; }
	public String getSortDirection() { return sortDirection; }
	public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
