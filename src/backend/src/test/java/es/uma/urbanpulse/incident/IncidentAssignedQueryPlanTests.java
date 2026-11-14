package es.uma.urbanpulse.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import es.uma.urbanpulse.PostgisContainerConfiguration;

@SpringBootTest
@Import(PostgisContainerConfiguration.class)
class IncidentAssignedQueryPlanTests {

	private static final String ASSIGNED_INCIDENT_QUERY = """
			SELECT "id" FROM "Incident"
			WHERE "assignee_id" = ? AND "status_id" IN (1, 2, 4, 5)
			ORDER BY "created_at" DESC, "id" DESC
			LIMIT 21
			""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void prepareSyntheticDataset() {
		jdbcTemplate.update("DELETE FROM \"StatusChange\"");
		jdbcTemplate.update("DELETE FROM \"Incident\"");
		Integer technicianId = jdbcTemplate.queryForObject(
				"SELECT id FROM \"User\" WHERE email = 'tecnico1@urbanpulse.test'", Integer.class);
		jdbcTemplate.update("""
				INSERT INTO "Incident" (title, description, location, status_id, assignee_id, created_at, updated_at)
				SELECT 'assigned-query-' || sample.id, 'synthetic benchmark row',
					ST_SetSRID(ST_MakePoint(-4.42, 36.72), 4326),
					CASE WHEN sample.id <= 18000 THEN 5 ELSE 6 END,
					CASE WHEN sample.id <= 18000 THEN ? ELSE NULL END,
					now() - (sample.id || ' seconds')::interval,
					now() - (sample.id || ' seconds')::interval
				FROM generate_series(1, 20000) AS sample(id)
				""", technicianId);
		jdbcTemplate.execute("ANALYZE \"Incident\"");
	}

	@Test
	void assignedQueueUsesItsIndexAgainstASequentialScanBaseline() {
		Integer technicianId = jdbcTemplate.queryForObject(
				"SELECT id FROM \"User\" WHERE email = 'tecnico1@urbanpulse.test'", Integer.class);
		List<String> baseline = explain(technicianId, true);
		List<String> indexed = explain(technicianId, false);

		assertThat(String.join("\n", baseline)).contains("Seq Scan");
		assertThat(String.join("\n", indexed)).contains("idx_incident_active_assignee_created_at");
		assertThat(executionTime(indexed)).isLessThan(executionTime(baseline));
	}

	private List<String> explain(Integer technicianId, boolean disableIndexScans) {
		return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
			try (var settings = connection.createStatement()) {
				settings.execute("SET enable_indexscan = " + (!disableIndexScans));
				settings.execute("SET enable_bitmapscan = " + (!disableIndexScans));
			}
			try (var statement = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) " + ASSIGNED_INCIDENT_QUERY)) {
				statement.setInt(1, technicianId);
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
