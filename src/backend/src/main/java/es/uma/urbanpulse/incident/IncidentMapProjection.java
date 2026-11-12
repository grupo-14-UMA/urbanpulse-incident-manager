package es.uma.urbanpulse.incident;

import java.time.Instant;

/** Scalar fields needed to render a map marker, including its category in the same query. */
public interface IncidentMapProjection {

	Integer getId();

	String getTitle();

	String getDescription();

	Double getLatitude();

	Double getLongitude();

	Integer getCategoryId();

	String getCategoryName();

	Integer getStatusId();

	Instant getCreatedAt();

	String getPriority();
}
