package es.uma.urbanpulse.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import es.uma.urbanpulse.PostgisContainerConfiguration;

/**
 * Measurement of the query behind the citizen "my incidents" list against its
 * (reporter_id, created_at DESC, id DESC) index, following the technician queue baseline.
 */
@SpringBootTest
@Import(PostgisContainerConfiguration.class)
class IncidentCitizenQueryPlanTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(IncidentCitizenQueryPlanTests.class);

	private static final String MINE_QUERY = """
			SELECT "id" FROM "Incident"
			WHERE "reporter_id" = ?
			ORDER BY "created_at" DESC, "id" DESC
			LIMIT 21
			""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void prepareSyntheticDataset() {
		jdbcTemplate.update("DELETE FROM \"StatusChange\"");
		jdbcTemplate.update("DELETE FROM \"Incident\"");
		// The reporter owns 2% of the rows, like a real citizen against a city-wide table;
		// with every row owned by one reporter the planner would rather filter another index.
		Integer reporterId = jdbcTemplate.queryForObject(
				"SELECT id FROM \"User\" WHERE email = 'tecnico1@urbanpulse.test'", Integer.class);
		Integer otherOwnerId = jdbcTemplate.queryForObject(
				"SELECT id FROM \"User\" WHERE email = 'operador@urbanpulse.test'", Integer.class);
		jdbcTemplate.update("""
				INSERT INTO "Incident" (title, description, location, status_id, reporter_id, created_at, updated_at)
				SELECT 'mine-query-' || sample.id, 'synthetic benchmark row',
					ST_SetSRID(ST_MakePoint(-4.42, 36.72), 4326),
					(sample.id % 6) + 1,
					CASE WHEN sample.id % 50 = 0 THEN ? ELSE ? END,
					now() - (sample.id || ' seconds')::interval,
					now() - (sample.id || ' seconds')::interval
				FROM generate_series(1, 20000) AS sample(id)
				""", reporterId, otherOwnerId);
		jdbcTemplate.execute("ANALYZE \"Incident\"");
	}

	@Test
	void citizenListUsesItsIndexAgainstASequentialScanBaseline() {
		Integer reporterId = jdbcTemplate.queryForObject(
				"SELECT id FROM \"User\" WHERE email = 'tecnico1@urbanpulse.test'", Integer.class);
		List<String> baseline = explain(reporterId, true);
		List<String> indexed = explain(reporterId, false);
		LOGGER.info("Citizen mine query plan with indexes disabled:\n{}", String.join("\n", baseline));
		LOGGER.info("Citizen mine query plan with indexes enabled:\n{}", String.join("\n", indexed));

		assertThat(String.join("\n", baseline)).contains("Seq Scan");
		assertThat(String.join("\n", indexed)).contains("idx_incident_reporter_created_at_id");
		assertThat(String.join("\n", indexed)).doesNotContain("Sort"); // The index returns the rows already ordered.
		assertThat(executionTime(indexed)).isLessThan(executionTime(baseline));
	}

	private List<String> explain(Integer reporterId, boolean disableIndexScans) {
		return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
			try (var settings = connection.createStatement()) {
				settings.execute("SET enable_indexscan = " + (!disableIndexScans));
				settings.execute("SET enable_bitmapscan = " + (!disableIndexScans));
			}
			try (var statement = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) " + MINE_QUERY)) {
				statement.setInt(1, reporterId);
				try (var result = statement.executeQuery()) {
					List<String> plan = new java.util.ArrayList<>();
					while (result.next()) {
						plan.add(result.getString(1));
					}
					return plan;
				}
			}
		});
	}

	private static double executionTime(List<String> plan) {
		return plan.stream()
				.filter(line -> line.startsWith("Execution Time:"))
				.map(line -> line.replace("Execution Time:", "").replace(" ms", "").trim())
				.mapToDouble(Double::parseDouble)
				.findFirst()
				.orElseThrow();
	}
}
