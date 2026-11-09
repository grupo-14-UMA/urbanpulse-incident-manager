package es.uma.urbanpulse.incident;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.locationtech.jts.geom.Point;

import es.uma.urbanpulse.category.Category;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"Incident\"")
public class Incident {

	private static final Clock CLOCK = Clock.systemUTC();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Column(name = "description", columnDefinition = "varchar")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_incident_category"))
	private Category category;

	/** Null only for incidents stored before authentication existed. */
	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "reporter_id", updatable = false, foreignKey = @ForeignKey(name = "fk_incident_reporter"))
	private User reporter;

	@Column(name = "location", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point location;

	@Convert(converter = IncidentStatusConverter.class)
	@Column(name = "status_id", nullable = false)
	private IncidentStatus status = IncidentStatus.REPORTED;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
	private Instant updatedAt;

	protected Incident() {
	}

	public Incident(String title, String description, Category category, Point location, User reporter) {
		this.title = title;
		this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
		this.description = description;
		this.category = category;
		this.location = toWgs84(location);
	}

	@PrePersist
	void onCreate() {
		Instant now = now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = now();
	}

	public Integer getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public User getReporter() {
		return reporter;
	}

	public Point getLocation() {
		return location;
	}

	public void setLocation(Point location) {
		this.location = toWgs84(location);
	}

	public IncidentStatus getStatus() {
		return status;
	}

	public void setStatus(IncidentStatus status) {
		this.status = Objects.requireNonNull(status, "status must not be null");
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	private static Instant now() {
		return Instant.now(CLOCK).truncatedTo(ChronoUnit.MILLIS);
	}

	private static Point toWgs84(Point location) {
		Objects.requireNonNull(location, "location must not be null");
		if (location.getSRID() != 0 && location.getSRID() != 4326) {
			throw new IllegalArgumentException("Incident location must use SRID 4326");
		}
		location.setSRID(4326);
		return location;
	}

}
