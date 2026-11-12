package es.uma.urbanpulse.incident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Integer> {

	@Override
	@EntityGraph(attributePaths = { "category", "assignee" })
	Optional<Incident> findById(Integer id);

	/** ST_Intersects applies an indexable bounding-box prefilter to the GiST-indexed location. */
	@Query(value = """
			SELECT incident."id" AS "id",
				incident."title" AS "title",
				incident."description" AS "description",
				ST_Y(incident."location") AS "latitude",
				ST_X(incident."location") AS "longitude",
				category."id" AS "categoryId",
				category."name" AS "categoryName",
				incident."status_id" AS "statusId",
				incident."created_at" AS "createdAt",
				incident."priority" AS "priority"
			FROM "Incident" incident
			LEFT JOIN "Category" category ON category."id" = incident."category_id"
			WHERE ST_Intersects(
				incident."location",
				ST_MakeEnvelope(:west, :south, :east, :north, 4326))
				AND incident."status_id" IN (:activeStatusIds)
			LIMIT :fetchLimit
			""", nativeQuery = true)
	List<IncidentMapProjection> findWithinBounds(
			@Param("west") double west,
			@Param("south") double south,
			@Param("east") double east,
			@Param("north") double north,
			@Param("activeStatusIds") List<Integer> activeStatusIds,
			@Param("fetchLimit") int fetchLimit);
}
