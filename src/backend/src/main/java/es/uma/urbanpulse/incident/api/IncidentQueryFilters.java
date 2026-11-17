package es.uma.urbanpulse.incident.api;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import es.uma.urbanpulse.incident.IncidentPriority;
import es.uma.urbanpulse.incident.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

/** Filters shared by the incident list and map queries. */
@Schema(description = "Filtros opcionales de incidencias.")
public abstract class IncidentQueryFilters {

	@Min(value = 1, message = "El identificador de categoría debe ser positivo.")
	private Integer categoryId;
	private boolean uncategorized;
	private IncidentStatus status;
	private IncidentPriority priority;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private Instant createdAfter;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private Instant createdBefore;

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
}
