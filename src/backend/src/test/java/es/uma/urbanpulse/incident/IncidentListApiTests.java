package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_2_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.sql.Timestamp;
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
import es.uma.urbanpulse.incident.api.IncidentPageResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentListApiTests {

	private static final int REPORTED = 1;
	private static final int VALIDATED = 2;
	private static final int REJECTED = 3;

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String operatorAuthorization;
	private Integer firstCategoryId;
	private Integer secondCategoryId;

	@BeforeEach
	void resetData() {
		jdbcTemplate.update("DELETE FROM \"StatusChange\"");
		jdbcTemplate.update("DELETE FROM \"Incident\"");
		operatorAuthorization = bearer(login(client, OPERATOR_EMAIL));
		List<Integer> categories = jdbcTemplate.query(
				"SELECT id FROM \"Category\" ORDER BY id LIMIT 2",
				(rs, row) -> rs.getInt(1));
		firstCategoryId = categories.get(0);
		secondCategoryId = categories.get(1);
	}

	@Test
	void appliesEveryFilterIndividuallyAndTogether() {
		Instant dayOne = Instant.parse("2026-11-10T08:00:00Z");
		Instant dayTwo = Instant.parse("2026-11-11T08:00:00Z");
		insertIncident("match", VALIDATED, firstCategoryId, "URGENT", null, null, dayTwo);
		insertIncident("other status", REPORTED, firstCategoryId, "URGENT", null, null, dayTwo);
		insertIncident("other category", VALIDATED, secondCategoryId, "URGENT", null, null, dayTwo);
		insertIncident("no category", VALIDATED, null, "URGENT", null, null, dayTwo);
		insertIncident("other priority", VALIDATED, firstCategoryId, "LOW", null, null, dayTwo);
		insertIncident("old date", VALIDATED, firstCategoryId, "URGENT", null, null, dayOne);

		assertTitles("?status=VALIDATED", "match", "other category", "no category", "other priority", "old date");
		assertTitles("?categoryId=" + firstCategoryId, "match", "other status", "other priority", "old date");
		assertTitles("?uncategorized=true", "no category");
		assertTitles("?priority=URGENT", "match", "other status", "other category", "no category", "old date");
		assertTitles("?createdAfter=2026-11-11T00:00:00Z&createdBefore=2026-11-12T00:00:00Z",
				"match", "other status", "other category", "no category", "other priority");
		assertTitles("?status=VALIDATED&categoryId=" + firstCategoryId
				+ "&priority=URGENT&createdAfter=2026-11-11T00:00:00Z"
				+ "&createdBefore=2026-11-12T00:00:00Z", "match");
	}

	@Test
	void paginatesWithStableDateOrderingAndRejectsUnboundedPageSizes() {
		insertIncident("old", REPORTED, firstCategoryId, "LOW", null, null, Instant.parse("2026-01-01T00:00:00Z"));
		insertIncident("middle", REPORTED, firstCategoryId, "MEDIUM", null, null, Instant.parse("2026-01-02T00:00:00Z"));
		insertIncident("new", REPORTED, firstCategoryId, "HIGH", null, null, Instant.parse("2026-01-03T00:00:00Z"));

		IncidentPageResponse first = loadPage("?page=0&size=2&sortBy=createdAt&sortDirection=desc");
		assertThat(first.incidents()).extracting(IncidentResponse::title).containsExactly("new", "middle");
		assertThat(first.page()).isZero();
		assertThat(first.size()).isEqualTo(2);
		assertThat(first.totalElements()).isEqualTo(3);
		assertThat(first.totalPages()).isEqualTo(2);

		IncidentPageResponse second = loadPage("?page=1&size=2&sortBy=createdAt&sortDirection=desc");
		assertThat(second.incidents()).extracting(IncidentResponse::title).containsExactly("old");

		client.get().uri("/api/incidents?size=101").header("Authorization", operatorAuthorization)
			.exchange().expectStatus().isBadRequest();
		client.get().uri("/api/incidents?size=0").header("Authorization", operatorAuthorization)
			.exchange().expectStatus().isBadRequest();
	}

	@Test
	void sortsByManualPriorityWithUnprioritizedIncidentsLast() {
		insertIncident("sin prioridad", REPORTED, firstCategoryId, null, null, null, Instant.parse("2026-01-03T00:00:00Z"));
		insertIncident("baja", REPORTED, firstCategoryId, "LOW", null, null, Instant.parse("2026-01-02T00:00:00Z"));
		insertIncident("urgente", REPORTED, firstCategoryId, "URGENT", null, null, Instant.parse("2026-01-01T00:00:00Z"));

		IncidentPageResponse response = loadPage("?sortBy=priority&sortDirection=desc");
		assertThat(response.incidents()).extracting(IncidentResponse::title)
				.containsExactly("urgente", "baja", "sin prioridad");
	}

	@Test
	void scopesListResultsToOperatorCitizenAndAssignedTechnician() {
		AuthResponse citizen = newCitizen(client);
		AuthResponse anotherCitizen = newCitizen(client);
		Integer technicianId = userId(TECHNICIAN_EMAIL);
		Integer otherTechnicianId = userId(TECHNICIAN_2_EMAIL);
		Integer ownRejected = insertIncident("propia rechazada", REJECTED, firstCategoryId, null,
				citizen.user().id(), null, Instant.parse("2026-01-01T00:00:00Z"));
		Integer otherReported = insertIncident("ajena", REPORTED, firstCategoryId, null,
				anotherCitizen.user().id(), null, Instant.parse("2026-01-02T00:00:00Z"));
		Integer assigned = insertIncident("asignada a técnico", VALIDATED, firstCategoryId, null,
				null, technicianId, Instant.parse("2026-01-03T00:00:00Z"));
		Integer assignedElsewhere = insertIncident("asignada a otro", VALIDATED, firstCategoryId, null,
				null, otherTechnicianId, Instant.parse("2026-01-04T00:00:00Z"));

		assertThat(loadPage(bearer(login(client, OPERATOR_EMAIL)), "").incidents())
				.extracting(IncidentResponse::id)
				.containsExactlyInAnyOrder(ownRejected, otherReported, assigned, assignedElsewhere);
		assertThat(loadPage(bearer(citizen), "").incidents())
				.extracting(IncidentResponse::id).containsExactly(ownRejected);
		assertThat(loadPage(bearer(login(client, TECHNICIAN_EMAIL)), "").incidents())
				.extracting(IncidentResponse::id).containsExactly(assigned);
	}

	@Test
	void validatesIncompleteMapBoundsAndConflictingCategoryFilters() {
		client.get().uri("/api/incidents?west=-4.45").header("Authorization", operatorAuthorization)
			.exchange().expectStatus().isBadRequest();
		client.get().uri("/api/incidents?categoryId=" + firstCategoryId + "&uncategorized=true")
			.header("Authorization", operatorAuthorization).exchange().expectStatus().isBadRequest();
	}

	private void assertTitles(String query, String... expected) {
		assertThat(loadPage(query).incidents()).extracting(IncidentResponse::title).containsExactlyInAnyOrder(expected);
	}

	private IncidentPageResponse loadPage(String query) {
		return loadPage(operatorAuthorization, query);
	}

	private IncidentPageResponse loadPage(String authorization, String query) {
		return client.get().uri("/api/incidents" + query).header("Authorization", authorization)
			.exchange().expectStatus().isOk().expectBody(IncidentPageResponse.class)
			.returnResult().getResponseBody();
	}

	private Integer insertIncident(String title, int statusId, Integer categoryId, String priority,
			Integer reporterId, Integer assigneeId, Instant createdAt) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO \"Incident\" (title, location, status_id, category_id, priority, reporter_id, "
						+ "assignee_id, created_at, updated_at) VALUES (?, ST_SetSRID(ST_MakePoint(-4.42, 36.72), 4326), "
						+ "?, ?, ?, ?, ?, ?, ?) RETURNING id",
				Integer.class, title, statusId, categoryId, priority, reporterId, assigneeId,
				Timestamp.from(createdAt), Timestamp.from(createdAt));
	}

	private Integer userId(String email) {
		return jdbcTemplate.queryForObject("SELECT id FROM \"User\" WHERE email = ?", Integer.class, email);
	}
}
