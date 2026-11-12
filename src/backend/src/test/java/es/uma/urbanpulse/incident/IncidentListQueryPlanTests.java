package es.uma.urbanpulse.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import es.uma.urbanpulse.PostgisContainerConfiguration;

/** Opt-in planner check against one million synthetic incidents. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgisContainerConfiguration.class)
@EnabledIfSystemProperty(named = "urbanpulse.performance-tests", matches = "true")
class IncidentListQueryPlanTests {

	private static final int SYNTHETIC_ROWS = 1_000_000;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final Map<String, String> observedPlans = new LinkedHashMap<>();

	@Test
	void listAccessPathsUseTheirIndexesAtOneMillionRows() {
		jdbcTemplate.update("DELETE FROM \"StatusChange\"");
		jdbcTemplate.update("DELETE FROM \"Incident\"");
		dropIndexesForBulkLoad();
		int reporterId = userId("operador@urbanpulse.test");
		int assigneeId = userId("tecnico1@urbanpulse.test");
		int firstCategoryId = categoryId(1);
		int secondCategoryId = categoryId(2);
		jdbcTemplate.update("""
				INSERT INTO "Incident" (title, location, status_id, category_id, priority, reporter_id,
					assignee_id, created_at, updated_at)
				SELECT 'synthetic-' || sample.id,
					ST_SetSRID(ST_MakePoint(-4.42, 36.72), 4326),
					(sample.id % 6) + 1,
					CASE WHEN sample.id % 5 = 0 THEN ? ELSE ? END,
					CASE
						WHEN sample.id % 100 = 0 THEN 'URGENT'
						WHEN sample.id % 100 BETWEEN 1 AND 5 THEN 'HIGH'
						WHEN sample.id % 100 BETWEEN 6 AND 25 THEN 'MEDIUM'
						WHEN sample.id % 100 BETWEEN 26 AND 75 THEN 'LOW'
						ELSE NULL
					END,
					CASE WHEN sample.id % 4 = 0 THEN ? ELSE NULL END,
					CASE WHEN sample.id % 10 = 0 THEN ? ELSE NULL END,
					now() - sample.id * interval '1 second',
					now() - sample.id * interval '1 second'
				FROM generate_series(1, ?) AS sample(id)
				""", firstCategoryId, secondCategoryId, reporterId, assigneeId, SYNTHETIC_ROWS);
		createProductionIndexes();
		jdbcTemplate.execute("ANALYZE \"Incident\"");

		assertPlanUses("idx_incident_created_at_id", """
				SELECT id FROM "Incident"
				WHERE created_at >= now() - interval '1 hour'
				ORDER BY created_at DESC, id DESC LIMIT 20
				""");
		assertPlanUses("idx_incident_status_created_at_id", """
				SELECT id FROM "Incident"
				WHERE status_id = 5
					AND created_at >= now() - interval '11.55 days'
					AND created_at < now() - interval '11.45 days'
				ORDER BY created_at DESC, id DESC LIMIT 20
				""");
		assertPlanUses("idx_incident_category_created_at_id", """
				SELECT id FROM "Incident" WHERE category_id = %d
				ORDER BY created_at DESC, id DESC LIMIT 20
				""".formatted(firstCategoryId));
		assertPlanUses("idx_incident_reporter_created_at_id", """
				SELECT id FROM "Incident" WHERE reporter_id = %d
				ORDER BY created_at DESC, id DESC LIMIT 20
				""".formatted(reporterId));
		assertPlanUses("idx_incident_assignee_created_at_id", """
				SELECT id FROM "Incident" WHERE assignee_id = %d
				ORDER BY created_at DESC, id DESC LIMIT 20
				""".formatted(assigneeId));
		assertPlanUses("idx_incident_priority_rank_created_at_id", """
				SELECT id FROM "Incident"
				ORDER BY (CASE priority
					WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 WHEN 'URGENT' THEN 4 ELSE 0 END) DESC,
					created_at DESC, id DESC LIMIT 20
				""");
		assertPlanUses("idx_incident_priority_created_at_id", """
				SELECT id FROM "Incident" WHERE priority = 'URGENT'
				ORDER BY created_at DESC, id DESC LIMIT 20
				""");

		List<String> missingIndexes = observedPlans.entrySet().stream()
				.filter(entry -> !entry.getValue().contains(entry.getKey()))
				.map(entry -> entry.getKey() + ":\n" + entry.getValue())
				.toList();
		assertThat(missingIndexes).as("Indexes expected in the million-row EXPLAIN plans").isEmpty();
	}

	private void assertPlanUses(String indexName, String query) {
		List<String> plan = jdbcTemplate.query("EXPLAIN (ANALYZE, BUFFERS) " + query,
				(rs, rowNumber) -> rs.getString(1));
		observedPlans.put(indexName, String.join("\n", plan));
	}

	private int userId(String email) {
		return jdbcTemplate.queryForObject("SELECT id FROM \"User\" WHERE email = ?", Integer.class, email);
	}

	private void dropIndexesForBulkLoad() {
		for (String index : List.of(
				"idx_incident_location_gist",
				"idx_incident_created_at_id",
				"idx_incident_status_created_at_id",
				"idx_incident_category_created_at_id",
				"idx_incident_reporter_created_at_id",
				"idx_incident_assignee_created_at_id",
				"idx_incident_priority_rank_created_at_id",
				"idx_incident_priority_created_at_id")) {
			jdbcTemplate.execute("DROP INDEX \"" + index + "\"");
		}
	}

	private void createProductionIndexes() {
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_location_gist\" ON \"Incident\" USING GIST (\"location\")");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_created_at_id\" ON \"Incident\" (\"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_status_created_at_id\" ON \"Incident\" (\"status_id\", \"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_category_created_at_id\" ON \"Incident\" (\"category_id\", \"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_reporter_created_at_id\" ON \"Incident\" (\"reporter_id\", \"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_assignee_created_at_id\" ON \"Incident\" (\"assignee_id\", \"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_priority_rank_created_at_id\" ON \"Incident\" ((CASE \"priority\" WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 WHEN 'URGENT' THEN 4 ELSE 0 END) DESC, \"created_at\" DESC, \"id\" DESC)");
		jdbcTemplate.execute("CREATE INDEX \"idx_incident_priority_created_at_id\" ON \"Incident\" (\"priority\", \"created_at\" DESC, \"id\" DESC)");
	}

	private int categoryId(int offset) {
		return jdbcTemplate.queryForObject(
				"SELECT id FROM \"Category\" ORDER BY id OFFSET ? LIMIT 1", Integer.class, offset - 1);
	}
}
