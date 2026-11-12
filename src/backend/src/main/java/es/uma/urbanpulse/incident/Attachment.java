package es.uma.urbanpulse.incident;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"Attachment\"")
public class Attachment {

	private static final Clock CLOCK = Clock.systemUTC();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "incident_id",
			nullable = false,
			updatable = false,
			foreignKey = @ForeignKey(name = "fk_attachment_incident"))
	private Incident incident;

	@Column(name = "storage_key", nullable = false, unique = true, updatable = false, length = 255)
	private String storageKey;

	@Column(name = "file_type", nullable = false, updatable = false, length = 50)
	private String fileType;

	@Column(name = "file_size", nullable = false, updatable = false)
	private long fileSize;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	protected Attachment() {
	}

	public Attachment(Incident incident, String storageKey, String fileType, long fileSize) {
		this.incident = Objects.requireNonNull(incident, "incident must not be null");
		this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
		this.fileType = Objects.requireNonNull(fileType, "fileType must not be null");
		if (fileSize <= 0) {
			throw new IllegalArgumentException("fileSize must be positive");
		}
		this.fileSize = fileSize;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now(CLOCK).truncatedTo(ChronoUnit.MILLIS);
	}

	public Integer getId() {
		return id;
	}

	public Incident getIncident() {
		return incident;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getFileType() {
		return fileType;
	}

	public long getFileSize() {
		return fileSize;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
