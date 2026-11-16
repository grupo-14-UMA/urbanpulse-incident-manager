package es.uma.urbanpulse.incident.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Opaque keyset position tied to the ordering used to produce it. */
public record IncidentCursor(
		String sortBy,
		String sortDirection,
		Instant createdAt,
		int id,
		int priorityRank) {

	private static final String SEPARATOR = "|";

	public String encode() {
		String value = String.join(SEPARATOR, sortBy, sortDirection, createdAt.toString(),
				Integer.toString(id), Integer.toString(priorityRank));
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	public boolean matches(String requestedSortBy, String requestedDirection) {
		return sortBy.equals(requestedSortBy) && sortDirection.equals(requestedDirection);
	}

	public static IncidentCursor decode(String encoded) {
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String[] fields = decoded.split("\\|", -1);
			if (fields.length != 5
					|| !(fields[0].equals("createdAt") || fields[0].equals("priority"))
					|| !(fields[1].equals("asc") || fields[1].equals("desc"))) {
				throw new IllegalArgumentException("Invalid incident cursor.");
			}
			Instant createdAt = Instant.parse(fields[2]);
			int id = Integer.parseInt(fields[3]);
			int priorityRank = Integer.parseInt(fields[4]);
			if (id <= 0 || priorityRank < 0 || priorityRank > 4) {
				throw new IllegalArgumentException("Invalid incident cursor.");
			}
			return new IncidentCursor(fields[0], fields[1], createdAt, id, priorityRank);
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid incident cursor.", exception);
		}
	}
}
