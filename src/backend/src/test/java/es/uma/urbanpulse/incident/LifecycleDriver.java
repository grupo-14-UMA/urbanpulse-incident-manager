package es.uma.urbanpulse.incident;

import static es.uma.urbanpulse.TestAccounts.OPERATOR_EMAIL;
import static es.uma.urbanpulse.TestAccounts.TECHNICIAN_EMAIL;
import static es.uma.urbanpulse.TestAccounts.bearer;
import static es.uma.urbanpulse.TestAccounts.login;
import static es.uma.urbanpulse.TestAccounts.newCitizen;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.springframework.test.web.servlet.client.RestTestClient;

import es.uma.urbanpulse.auth.AuthResponse;
import es.uma.urbanpulse.incident.api.IncidentResponse;

/** Drives incidents through the lifecycle with real tokens, so each test starts from the status it needs. */
final class LifecycleDriver {

	static final Map<String, Object> NEW_INCIDENT = Map.of(
			"title", "Farola fundida",
			"description", "La farola lleva varias noches sin encenderse.",
			"latitude", 36.7196,
			"longitude", -4.4216);

	final RestTestClient client;
	final AuthResponse citizen;
	final AuthResponse operator;
	final AuthResponse technician;

	LifecycleDriver(RestTestClient client) {
		this.client = client;
		this.citizen = newCitizen(client);
		this.operator = login(client, OPERATOR_EMAIL);
		this.technician = login(client, TECHNICIAN_EMAIL);
	}

	/** Reports an incident as the citizen: it is REPORTED. */
	int report() {
		IncidentResponse created = this.client.post()
			.uri("/api/incidents")
			.header("Authorization", bearer(this.citizen))
			.body(NEW_INCIDENT)
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(IncidentResponse.class)
			.returnResult()
			.getResponseBody();
		assertThat(created).isNotNull();
		return created.id();
	}

	int validated() {
		int id = report();
		post(id, "validate", this.operator, Map.of());
		return id;
	}

	int assigned() {
		int id = validated();
		post(id, "assign", this.operator, Map.of("technicianId", this.technician.user().id()));
		return id;
	}

	int inProgress() {
		int id = assigned();
		post(id, "start", this.technician, null);
		return id;
	}

	int rejected() {
		int id = report();
		post(id, "reject", this.operator, Map.of("reason", "Duplicada."));
		return id;
	}

	int resolved() {
		int id = inProgress();
		post(id, "resolve", this.technician, Map.of("note", "Arreglada."));
		return id;
	}

	void post(int id, String action, AuthResponse as, Object body) {
		request(id, action, as, body).expectStatus().isOk();
	}

	RestTestClient.ResponseSpec request(int id, String action, AuthResponse as, Object body) {
		var spec = this.client.post()
			.uri("/api/incidents/" + id + "/" + action)
			.header("Authorization", bearer(as));
		return (body == null ? spec : spec.body(body)).exchange();
	}

	RestTestClient.ResponseSpec putPriority(int id, AuthResponse as, Object body) {
		return this.client.put()
			.uri("/api/incidents/" + id + "/priority")
			.header("Authorization", bearer(as))
			.body(body)
			.exchange();
	}

	RestTestClient.ResponseSpec history(int id, AuthResponse as) {
		return this.client.get()
			.uri("/api/incidents/" + id + "/history")
			.header("Authorization", bearer(as))
			.exchange();
	}

	RestTestClient.ResponseSpec get(int id, AuthResponse as) {
		return this.client.get()
			.uri("/api/incidents/" + id)
			.header("Authorization", bearer(as))
			.exchange();
	}

}
