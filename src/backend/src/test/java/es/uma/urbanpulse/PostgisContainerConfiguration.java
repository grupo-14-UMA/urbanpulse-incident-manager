package es.uma.urbanpulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL with PostGIS, the same engine as production. Import it from any test that needs the
 * database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgisContainerConfiguration {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName.parse("postgis/postgis:17-3.5")
		.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgisContainer() {
		return new PostgreSQLContainer(POSTGIS_IMAGE);
	}

}
