package es.uma.urbanpulse.incident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Integer> {

	@Override
	@EntityGraph(attributePaths = "category")
	Optional<Incident> findById(Integer id);

	@EntityGraph(attributePaths = "category")
	List<Incident> findAllByOrderByCreatedAtDesc();
}
