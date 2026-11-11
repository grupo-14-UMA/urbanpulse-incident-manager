package es.uma.urbanpulse.incident;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Integer> {

	@Override
	@EntityGraph(attributePaths = "category")
	Optional<Incident> findById(Integer id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT incident FROM Incident incident WHERE incident.id = :id")
	Optional<Incident> findByIdForUpdate(@Param("id") Integer id);

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
				incident."created_at" AS "createdAt"
			FROM "Incident" incident
			LEFT JOIN "Category" category ON category."id" = incident."category_id"
			WHERE ST_Intersects(
				incident."location",
				ST_MakeEnvelope(:west, :south, :east, :north, 4326))
				AND incident."status_id" = :publicStatusId
			LIMIT :fetchLimit
			""", nativeQuery = true)
	List<IncidentMapProjection> findWithinBounds(
			@Param("west") double west,
			@Param("south") double south,
			@Param("east") double east,
			@Param("north") double north,
			@Param("publicStatusId") int publicStatusId,
			@Param("fetchLimit") int fetchLimit);

	/** Citizen map query; reporter_id is indexed together with created_at. */
	@Query(value = """
			SELECT incident."id" AS "id",
				incident."title" AS "title",
				incident."description" AS "description",
				ST_Y(incident."location") AS "latitude",
				ST_X(incident."location") AS "longitude",
				category."id" AS "categoryId",
				category."name" AS "categoryName",
				incident."status_id" AS "statusId",
				incident."created_at" AS "createdAt"
			FROM "Incident" incident
			LEFT JOIN "Category" category ON category."id" = incident."category_id"
			WHERE ST_Intersects(
				incident."location",
				ST_MakeEnvelope(:west, :south, :east, :north, 4326))
				AND incident."status_id" = :publicStatusId
				AND incident."reporter_id" = :reporterId
			LIMIT :fetchLimit
			""", nativeQuery = true)
	List<IncidentMapProjection> findWithinBoundsForReporter(
			@Param("west") double west,
			@Param("south") double south,
			@Param("east") double east,
			@Param("north") double north,
			@Param("publicStatusId") int publicStatusId,
			@Param("reporterId") int reporterId,
			@Param("fetchLimit") int fetchLimit);
}
