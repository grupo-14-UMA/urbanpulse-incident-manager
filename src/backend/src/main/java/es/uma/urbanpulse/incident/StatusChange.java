package es.uma.urbanpulse.incident;

import java.time.Instant;
import java.util.Objects;

import es.uma.urbanpulse.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One entry of the history of statuses of an incident. Append-only: never updated or deleted. */
@Entity
@Table(name = "\"StatusChange\"")
public class StatusChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incident_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_status_change_incident"))
	private Incident incident;

	/** Null only for the initial entry. */
	@Convert(converter = IncidentStatusConverter.class)
	@Column(name = "previous_status_id", updatable = false)
	private IncidentStatus previousStatus;

	@Convert(converter = IncidentStatusConverter.class)
	@Column(name = "new_status_id", nullable = false, updatable = false)
	private IncidentStatus newStatus;

	/** Null only for incidents stored before authentication existed. */
	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "changed_by_id", updatable = false,
			foreignKey = @ForeignKey(name = "fk_status_change_changed_by"))
	private User changedBy;

	@Column(name = "changed_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant changedAt;

	@Column(name = "note", length = 1000, updatable = false)
	private String note;

	protected StatusChange() {
	}

	public StatusChange(Incident incident, IncidentStatus previousStatus, IncidentStatus newStatus, User changedBy,
			Instant changedAt, String note) {
		this.incident = Objects.requireNonNull(incident, "incident must not be null");
		this.previousStatus = previousStatus;
		this.newStatus = Objects.requireNonNull(newStatus, "newStatus must not be null");
		this.changedBy = changedBy;
		this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public IncidentStatus getPreviousStatus() {
		return previousStatus;
	}

	public IncidentStatus getNewStatus() {
		return newStatus;
	}

	public User getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	public String getNote() {
		return note;
	}

}
