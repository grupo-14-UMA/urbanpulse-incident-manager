package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.PostgisContainerConfiguration;
import es.uma.urbanpulse.auth.AuthResponse;
import es.uma.urbanpulse.incident.api.IncidentMapResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentMapQueryTests {

	private static final String MAP_BOUNDS =
			"/api/incidents?west=-4.45&south=36.68&east=-4.38&north=36.77";
	private static final int REPORTED_STATUS_ID = 1;
	// Only REPORTED is defined by the MVP; other ids represent non-public, future statuses.
	private static final int NON_REPORTED_STATUS_ID = 2;

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String authorization;

	@BeforeEach
	void clearIncidents() {
		jdbcTemplate.update("DELETE FROM \"Incident\"");
		AuthResponse operator = login(this.client, OPERATOR_EMAIL);
		this.authorization = bearer(operator);
	}

	@Test
	void returnsOnlyVisibleReportedIncidents() {
		insertIncident("visible", -4.42, 36.72, REPORTED_STATUS_ID);
		insertIncident("outside", -4.50, 36.72, REPORTED_STATUS_ID);
		insertIncident("non-reported", -4.42, 36.72, NON_REPORTED_STATUS_ID);

		IncidentMapResponse response = loadMapResponse();

		assertThat(response.incidents())
				.extracting(IncidentResponse::title)
				.containsExactly("visible");
		assertThat(response.truncated()).isFalse();
	}

	@Test
	void requiresBoundsInsteadOfReturningAnUnboundedList() {
		client.get()
				.uri("/api/incidents")
				.header("Authorization", this.authorization)
				.exchange()
				.expectStatus()
				.isBadRequest();
	}

	@Test
	void rejectsInvertedOutOfRangeAndMalformedBounds() {
		for (String invalidQuery : List.of(
				"/api/incidents?west=-4.38&south=36.68&east=-4.45&north=36.77",
				"/api/incidents?west=-4.45&south=36.77&east=-4.38&north=36.68",
				"/api/incidents?west=-181&south=36.68&east=-4.38&north=36.77",
				"/api/incidents?west=-4.45&south=36.68&east=-4.38&north=95",
				"/api/incidents?west=invalid&south=36.68&east=-4.38&north=36.77")) {
			String body = client.get()
					.uri(invalidQuery)
					.header("Authorization", this.authorization)
					.exchange()
					.expectStatus()
					.isBadRequest()
					.expectBody(String.class)
					.returnResult()
					.getResponseBody();

			assertThat(body).doesNotContain("java.lang", "Failed to convert");
		}
	}

	@Test
	void capsResultsAndSignalsThatTheMapShouldZoomIn() {
		insertReportedIncidents(501);

		IncidentMapResponse response = loadMapResponse();

		assertThat(response.incidents()).hasSize(500);
		assertThat(response.truncated()).isTrue();
	}

	@Test
	void doesNotMarkAFullButUntruncatedResultAsTruncated() {
		insertReportedIncidents(500);

		IncidentMapResponse response = loadMapResponse();

		assertThat(response.incidents()).hasSize(500);
		assertThat(response.truncated()).isFalse();
	}

	@Test
	void explainUsesTheGiSTIndexForASelectiveMapArea() {
		insertSpatialGrid(20_000);
		jdbcTemplate.execute("ANALYZE \"Incident\"");

		List<String> plan = jdbcTemplate.query(
				"EXPLAIN (ANALYZE, BUFFERS) "
						+ "SELECT incident.\"id\" FROM \"Incident\" incident "
						+ "WHERE ST_Intersects(incident.\"location\", "
						+ "ST_MakeEnvelope(-4.422, 36.720, -4.420, 36.722, 4326)) "
						+ "AND incident.\"status_id\" = 1 LIMIT 501",
				(rs, rowNumber) -> rs.getString(1));

		assertThat(String.join("\n", plan)).contains("idx_incident_location_gist");
	}

	private IncidentMapResponse loadMapResponse() {
		return client.get()
				.uri(MAP_BOUNDS)
				.header("Authorization", this.authorization)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(IncidentMapResponse.class)
				.returnResult()
				.getResponseBody();
	}

	private void insertIncident(String title, double longitude, double latitude, int statusId) {
		jdbcTemplate.update(
				"INSERT INTO \"Incident\" (title, location, status_id) "
						+ "VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)",
				title, longitude, latitude, statusId);
	}

	private void insertReportedIncidents(int count) {
		jdbcTemplate.update(
				"INSERT INTO \"Incident\" (title, location, status_id) "
						+ "SELECT 'reported-' || sample.id, "
						+ "ST_SetSRID(ST_MakePoint(-4.42, 36.72), 4326), 1 "
						+ "FROM generate_series(1, ?) AS sample(id)",
				count);
	}

	private void insertSpatialGrid(int count) {
		jdbcTemplate.update(
				"INSERT INTO \"Incident\" (title, location, status_id) "
						+ "SELECT 'grid-' || sample.id, "
						+ "ST_SetSRID(ST_MakePoint(-4.55 + (sample.id % 200) * 0.0015, "
						+ "36.60 + (sample.id / 200) * 0.003), 4326), 1 "
						+ "FROM generate_series(1, ?) AS sample(id)",
				count);
	}
}
