package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_2_EMAIL;
import static es.uma.urbanpulse.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntSupplier;

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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgisContainerConfiguration.class)
class IncidentLifecycleApiTests {

	private static final String NOT_ALLOWED = "No tienes permiso para hacer esto.";
	private static final String INVALID = "La solicitud contiene datos no válidos.";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private LifecycleDriver flow;

	@BeforeEach
	void newFlow() {
		this.flow = new LifecycleDriver(this.client);
	}

	private int historySize(int id) {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM \"StatusChange\" WHERE incident_id = ?", Integer.class, id);
	}

	// 1. Every transition, with its history entry

	@Test
	void validateMovesToValidatedAndAddsOneHistoryEntry() {
		int id = this.flow.report();

		this.flow.request(id, "validate", this.flow.operator, Map.of()).expectStatus().isOk()
			.expectBody().jsonPath("$.status").isEqualTo("VALIDATED");

		this.flow.history(id, this.flow.operator).expectStatus().isOk().expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[1].previousStatus").isEqualTo("REPORTED")
			.jsonPath("$[1].newStatus").isEqualTo("VALIDATED")
			.jsonPath("$[1].actorRole").isEqualTo("OPERATOR")
			.jsonPath("$[1].note").isEmpty();
	}

	@Test
	void rejectStoresTheReasonAsTheNote() {
		int id = this.flow.report();

		this.flow.request(id, "reject", this.flow.operator, Map.of("reason", "  Duplicada.  ")).expectStatus().isOk()
			.expectBody().jsonPath("$.status").isEqualTo("REJECTED");

		this.flow.history(id, this.flow.operator).expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[1].newStatus").isEqualTo("REJECTED")
			.jsonPath("$[1].actorRole").isEqualTo("OPERATOR")
			.jsonPath("$[1].note").isEqualTo("Duplicada.");
	}

	@Test
	void assignSetsTheAssigneeAndTheStatus() {
		int id = this.flow.validated();

		this.flow.request(id, "assign", this.flow.operator, Map.of("technicianId", this.flow.technician.user().id()))
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.status").isEqualTo("ASSIGNED")
			.jsonPath("$.assignee.id").isEqualTo(this.flow.technician.user().id())
			.jsonPath("$.assignee.name").isEqualTo(this.flow.technician.user().name());

		this.flow.history(id, this.flow.operator).expectBody()
			.jsonPath("$.length()").isEqualTo(3)
			.jsonPath("$[2].previousStatus").isEqualTo("VALIDATED")
			.jsonPath("$[2].newStatus").isEqualTo("ASSIGNED")
			.jsonPath("$[2].actorRole").isEqualTo("OPERATOR");
	}

	@Test
	void startAndResolveByTheAssigneeAreAuditedWithTheNote() {
		int id = this.flow.assigned();

		this.flow.request(id, "start", this.flow.technician, null).expectStatus().isOk()
			.expectBody().jsonPath("$.status").isEqualTo("IN_PROGRESS");
		this.flow.request(id, "resolve", this.flow.technician, Map.of("note", "Bombilla cambiada.")).expectStatus()
			.isOk().expectBody().jsonPath("$.status").isEqualTo("RESOLVED");

		this.flow.history(id, this.flow.operator).expectBody()
			.jsonPath("$.length()").isEqualTo(5)
			.jsonPath("$[3].previousStatus").isEqualTo("ASSIGNED")
			.jsonPath("$[3].newStatus").isEqualTo("IN_PROGRESS")
			.jsonPath("$[3].actorRole").isEqualTo("TECHNICIAN")
			.jsonPath("$[3].note").isEmpty()
			.jsonPath("$[4].previousStatus").isEqualTo("IN_PROGRESS")
			.jsonPath("$[4].newStatus").isEqualTo("RESOLVED")
			.jsonPath("$[4].actorRole").isEqualTo("TECHNICIAN")
			.jsonPath("$[4].note").isEqualTo("Bombilla cambiada.");
	}

	@Test
	void changedByIsStoredForAuditing() {
		int id = this.flow.validated();

		Integer changedBy = this.jdbcTemplate.queryForObject(
				"SELECT changed_by_id FROM \"StatusChange\" WHERE incident_id = ? AND new_status_id = 2",
				Integer.class, id);
		assertThat(changedBy).isEqualTo(this.flow.operator.user().id());
	}

	// 2. Validate with priority

	@Test
	void validateStoresPriorityAndJustification() {
		int id = this.flow.report();

		this.flow.request(id, "validate", this.flow.operator,
				Map.of("priority", "URGENT", "priorityJustification", "  Cables al aire.  "))
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.priority").isEqualTo("URGENT")
			.jsonPath("$.priorityJustification").isEqualTo("Cables al aire.");
	}

	@Test
	void validateWithoutBodyOrWithEmptyBodyLeavesNoPriority() {
		int withoutBody = this.flow.report();
		this.flow.request(withoutBody, "validate", this.flow.operator, null).expectStatus().isOk()
			.expectBody().jsonPath("$.priority").isEmpty();

		int emptyBody = this.flow.report();
		this.flow.request(emptyBody, "validate", this.flow.operator, Map.of()).expectStatus().isOk()
			.expectBody().jsonPath("$.priority").isEmpty().jsonPath("$.priorityJustification").isEmpty();
	}

	@Test
	void justificationWithoutPriorityIsRejected() {
		int id = this.flow.report();

		this.flow.request(id, "validate", this.flow.operator, Map.of("priorityJustification", "Urge."))
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.message").isEqualTo(INVALID)
			.jsonPath("$.errors.priority").isEqualTo("Indica la prioridad si añades una justificación.");
	}

	@Test
	void unknownPriorityIsAnInvalidBody() {
		int id = this.flow.report();

		this.flow.request(id, "validate", this.flow.operator, Map.of("priority", "VERY_HIGH"))
			.expectStatus().isBadRequest();
	}

	// 3. Change priority

	@Test
	void changePriorityOnOpenAcceptedWorkReplacesPriorityAndJustificationWithoutHistory() {
		for (IntSupplier stage : List.<IntSupplier>of(this.flow::validated, this.flow::assigned,
				this.flow::inProgress)) {
			int id = stage.getAsInt();
			int before = historySize(id);

			this.flow.putPriority(id, this.flow.operator, Map.of("priority", "HIGH", "justification", "Primera."))
				.expectStatus().isOk();
			this.flow.putPriority(id, this.flow.operator, Map.of("priority", "LOW")).expectStatus().isOk()
				.expectBody()
				.jsonPath("$.priority").isEqualTo("LOW")
				.jsonPath("$.priorityJustification").isEmpty();

			assertThat(historySize(id)).isEqualTo(before);
		}
	}

	@Test
	void changePriorityIsRejectedOutsideAcceptedOpenWork() {
		for (IntSupplier stage : List.<IntSupplier>of(this.flow::report, this.flow::rejected, this.flow::resolved)) {
			this.flow.putPriority(stage.getAsInt(), this.flow.operator, Map.of("priority", "HIGH"))
				.expectStatus().isEqualTo(409);
		}
	}

	@Test
	void changePriorityRequiresAPriority() {
		int id = this.flow.validated();

		this.flow.putPriority(id, this.flow.operator, Map.of("justification", "Algo.")).expectStatus()
			.isBadRequest()
			.expectBody().jsonPath("$.errors.priority").isEqualTo("La prioridad es obligatoria.");
	}

	// 4. Required and bounded texts

	@Test
	void rejectAndResolveRequireATextOfAtMost1000Characters() {
		int toReject = this.flow.report();
		int toResolve = this.flow.inProgress();
		String tooLong = "a".repeat(1001);

		for (Object body : new Object[] { Map.of(), Map.of("reason", "   "), Map.of("reason", tooLong) }) {
			this.flow.request(toReject, "reject", this.flow.operator, body).expectStatus().isBadRequest();
		}
		for (Object body : new Object[] { Map.of(), Map.of("note", "  "), Map.of("note", tooLong) }) {
			this.flow.request(toResolve, "resolve", this.flow.technician, body).expectStatus().isBadRequest();
		}
		this.flow.request(toReject, "reject", this.flow.operator, Map.of()).expectBody()
			.jsonPath("$.errors.reason").isEqualTo("El motivo del rechazo es obligatorio.");
		this.flow.request(toResolve, "resolve", this.flow.technician, Map.of("note", " ")).expectBody()
			.jsonPath("$.errors.note").isEqualTo("La nota de resolución es obligatoria.");
		this.flow.request(toReject, "reject", this.flow.operator, Map.of("reason", tooLong)).expectBody()
			.jsonPath("$.errors.reason").isEqualTo("El motivo del rechazo no puede superar los 1000 caracteres.");
		this.flow.putPriority(this.flow.validated(), this.flow.operator,
				Map.of("priority", "LOW", "justification", tooLong)).expectStatus().isBadRequest()
			.expectBody().jsonPath("$.errors.justification")
			.isEqualTo("La justificación no puede superar los 1000 caracteres.");
	}

	// 5. Invalid transitions

	@Test
	void invalidTransitionsAreConflictsWithTheCurrentStatusInSpanish() {
		AuthResponse op = this.flow.operator;
		AuthResponse tech = this.flow.technician;
		Map<String, Object> assignBody = Map.of("technicianId", tech.user().id());

		int validated = this.flow.validated();
		expectConflict(validated, "validate", op, Map.of(), "Validada");
		expectConflict(validated, "reject", op, Map.of("reason", "x"), "Validada");
		int reported = this.flow.report();
		expectConflict(reported, "assign", op, assignBody, "Registrada");
		// start and resolve check the assignee first, so use an assigned Incidencia.
		int assigned = this.flow.assigned();
		expectConflict(assigned, "assign", op, assignBody, "Asignada");
		expectConflict(assigned, "resolve", tech, Map.of("note", "x"), "Asignada");
		int inProgress = this.flow.inProgress();
		expectConflict(inProgress, "start", tech, null, "En curso");

		int rejected = this.flow.rejected();
		expectConflict(rejected, "validate", op, Map.of(), "Rechazada");
		expectConflict(rejected, "reject", op, Map.of("reason", "x"), "Rechazada");
		expectConflict(rejected, "assign", op, assignBody, "Rechazada");

		int resolved = this.flow.resolved();
		expectConflict(resolved, "validate", op, Map.of(), "Resuelta");
		expectConflict(resolved, "reject", op, Map.of("reason", "x"), "Resuelta");
		expectConflict(resolved, "assign", op, assignBody, "Resuelta");
		expectConflict(resolved, "start", tech, null, "Resuelta");
		expectConflict(resolved, "resolve", tech, Map.of("note", "x"), "Resuelta");
	}

	@Test
	void assigningAnAssignedIncidentIsAConflictWhateverTheBodySays() {
		int id = this.flow.assigned();

		expectConflict(id, "assign", this.flow.operator, Map.of("technicianId", 999999), "Asignada");
	}

	@Test
	void startFromValidatedAsTheOnlyTechnicianWhoIsNotAssigneeIsForbidden() {
		int id = this.flow.validated();

		this.flow.request(id, "start", this.flow.technician, null).expectStatus().isForbidden();
	}

	private void expectConflict(int id, String action, AuthResponse as, Object body, String label) {
		this.flow.request(id, action, as, body).expectStatus().isEqualTo(409).expectBody()
			.jsonPath("$.message").isEqualTo("La incidencia está «" + label + "» y no admite esta acción.");
	}

	// 6 and 7. Roles and ownership

	@Test
	void actionsReservedToAnotherRoleAreForbidden() {
		int id = this.flow.report();
		int technicianId = this.flow.technician.user().id();

		for (AuthResponse wrong : new AuthResponse[] { this.flow.citizen, this.flow.technician }) {
			this.flow.request(id, "validate", wrong, Map.of()).expectStatus().isForbidden()
				.expectBody().jsonPath("$.message").isEqualTo(NOT_ALLOWED);
			this.flow.request(id, "reject", wrong, Map.of("reason", "x")).expectStatus().isForbidden();
			this.flow.request(id, "assign", wrong, Map.of("technicianId", technicianId)).expectStatus().isForbidden();
			this.flow.putPriority(id, wrong, Map.of("priority", "LOW")).expectStatus().isForbidden();
		}
		for (AuthResponse wrong : new AuthResponse[] { this.flow.citizen, this.flow.operator }) {
			this.flow.request(id, "start", wrong, null).expectStatus().isForbidden()
				.expectBody().jsonPath("$.message").isEqualTo(NOT_ALLOWED);
			this.flow.request(id, "resolve", wrong, Map.of("note", "x")).expectStatus().isForbidden();
		}
	}

	@Test
	void roleIsCheckedBeforeTheBody() {
		int id = this.flow.report();

		this.flow.request(id, "reject", this.flow.citizen, Map.of()).expectStatus().isForbidden();
	}

	@Test
	void actionsRequireAToken() {
		this.client.post().uri("/api/incidents/1/validate").exchange().expectStatus().isUnauthorized();
		this.client.get().uri("/api/incidents/1/history").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void anotherTechnicianCannotStartOrResolve() {
		AuthResponse other = login(this.client, TECHNICIAN_2_EMAIL);
		int assigned = this.flow.assigned();
		int inProgress = this.flow.inProgress();

		this.flow.request(assigned, "start", other, null).expectStatus().isForbidden()
			.expectBody().jsonPath("$.message").isEqualTo(NOT_ALLOWED);
		this.flow.request(inProgress, "resolve", other, Map.of("note", "x")).expectStatus().isForbidden();
	}

	// 8. Technician checks

	@Test
	void assignToAnUnknownUserOrANonTechnicianIsABadRequest() {
		int id = this.flow.validated();

		for (int target : new int[] { 999999, this.flow.operator.user().id(), this.flow.citizen.user().id() }) {
			this.flow.request(id, "assign", this.flow.operator, Map.of("technicianId", target)).expectStatus()
				.isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo(INVALID)
				.jsonPath("$.errors.technicianId").isEqualTo("El técnico elegido no existe.");
		}
		this.flow.request(id, "assign", this.flow.operator, Map.of()).expectStatus().isBadRequest()
			.expectBody().jsonPath("$.errors.technicianId").isEqualTo("Elige un técnico.");
	}

	// 9. Unknown incident

	@Test
	void unknownIncidentIsNotFoundOnEveryAction() {
		int missing = 2_000_000_000;

		this.flow.request(missing, "validate", this.flow.operator, Map.of()).expectStatus().isNotFound();
		this.flow.request(missing, "reject", this.flow.operator, Map.of("reason", "x")).expectStatus().isNotFound();
		this.flow.request(missing, "assign", this.flow.operator, Map.of("technicianId", 1)).expectStatus()
			.isNotFound();
		this.flow.putPriority(missing, this.flow.operator, Map.of("priority", "LOW")).expectStatus().isNotFound();
		this.flow.request(missing, "start", this.flow.technician, null).expectStatus().isNotFound();
		this.flow.request(missing, "resolve", this.flow.technician, Map.of("note", "x")).expectStatus().isNotFound();
		this.flow.history(missing, this.flow.operator).expectStatus().isNotFound();
	}

	// 11. Assignee visibility

	@Test
	void assigneeIsVisibleToStaffAndNeverToCitizens() {
		int id = this.flow.assigned();

		this.flow.get(id, this.flow.operator).expectBody().jsonPath("$.assignee.id")
			.isEqualTo(this.flow.technician.user().id());
		this.flow.get(id, this.flow.technician).expectBody().jsonPath("$.assignee.id")
			.isEqualTo(this.flow.technician.user().id());
		this.flow.get(id, this.flow.citizen).expectBody().jsonPath("$.assignee").isEmpty();
		this.flow.get(id, this.flow.citizen).expectBody().jsonPath("$.priority").isEmpty();
	}

	@Test
	void citizenSeesThePriorityButNotTheAssignee() {
		int id = this.flow.report();
		this.flow.post(id, "validate", this.flow.operator, Map.of("priority", "HIGH"));
		this.flow.post(id, "assign", this.flow.operator, Map.of("technicianId", this.flow.technician.user().id()));

		this.flow.get(id, this.flow.citizen).expectBody()
			.jsonPath("$.priority").isEqualTo("HIGH")
			.jsonPath("$.assignee").isEmpty();
	}

	// 12. Map

	@Test
	void mapKeepsOpenIncidentsAndDropsRejectedAndResolved() {
		String bounds = "/api/incidents?west=-4.4217&south=36.7195&east=-4.4215&north=36.7197";
		int validated = this.flow.validated();
		int assigned = this.flow.assigned();
		int inProgress = this.flow.inProgress();
		int rejected = this.flow.rejected();
		int resolved = this.flow.resolved();
		this.flow.putPriority(validated, this.flow.operator, Map.of("priority", "URGENT")).expectStatus().isOk();

		byte[] body = this.client.get().uri(bounds)
			.header("Authorization", es.uma.urbanpulse.TestAccounts.bearer(this.flow.operator))
			.exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
		String json = new String(body);

		assertThat(json).contains("\"id\":" + validated + ",", "\"id\":" + assigned + ",",
				"\"id\":" + inProgress + ",");
		assertThat(json).doesNotContain("\"id\":" + rejected + ",", "\"id\":" + resolved + ",");
		assertThat(json).contains("\"priority\":\"URGENT\"");
		assertThat(json).doesNotContain("\"assignee\":{");
	}

	// 13. Concurrency

	@Test
	void twoConcurrentActionsOnTheSameIncidentCannotBothWin() throws Exception {
		int id = this.flow.report();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch go = new CountDownLatch(1);
		try {
			var validate = pool.submit(() -> {
				go.await();
				return this.flow.request(id, "validate", this.flow.operator, Map.of()).returnResult(String.class)
					.getStatus().value();
			});
			var reject = pool.submit(() -> {
				go.await();
				return this.flow.request(id, "reject", this.flow.operator, Map.of("reason", "x"))
					.returnResult(String.class).getStatus().value();
			});
			go.countDown();
			List<Integer> statuses = List.of(validate.get(), reject.get());

			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
			assertThat(historySize(id)).isEqualTo(2);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
