package es.uma.urbanpulse.incident;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Integer>, JpaSpecificationExecutor<Incident> {

	@Override
	@EntityGraph(attributePaths = { "category", "assignee" })
	Optional<Incident> findById(Integer id);

	@Override
	@EntityGraph(attributePaths = { "category", "assignee" })
	Page<Incident> findAll(Specification<Incident> specification, Pageable pageable);

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
				incident."created_at" AS "createdAt",
				incident."priority" AS "priority"
			FROM "Incident" incident
			LEFT JOIN "Category" category ON category."id" = incident."category_id"
			WHERE ST_Intersects(
				incident."location",
				ST_MakeEnvelope(:west, :south, :east, :north, 4326))
				AND incident."status_id" IN (:activeStatusIds)
				AND (:statusId IS NULL OR incident."status_id" = :statusId)
				AND (:categoryId IS NULL OR incident."category_id" = :categoryId)
				AND (NOT :uncategorized OR incident."category_id" IS NULL)
				AND (:priority IS NULL OR incident."priority" = :priority)
				AND (CAST(:createdAfter AS timestamptz) IS NULL OR incident."created_at" >= :createdAfter)
				AND (CAST(:createdBefore AS timestamptz) IS NULL OR incident."created_at" < :createdBefore)
			LIMIT :fetchLimit
			""", nativeQuery = true)
	List<IncidentMapProjection> findWithinBounds(
			@Param("west") double west,
			@Param("south") double south,
			@Param("east") double east,
			@Param("north") double north,
			@Param("activeStatusIds") List<Integer> activeStatusIds,
			@Param("statusId") Integer statusId,
			@Param("categoryId") Integer categoryId,
			@Param("uncategorized") boolean uncategorized,
			@Param("priority") String priority,
			@Param("createdAfter") java.time.Instant createdAfter,
			@Param("createdBefore") java.time.Instant createdBefore,
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
				incident."created_at" AS "createdAt",
				incident."priority" AS "priority"
			FROM "Incident" incident
			LEFT JOIN "Category" category ON category."id" = incident."category_id"
			WHERE ST_Intersects(
				incident."location",
				ST_MakeEnvelope(:west, :south, :east, :north, 4326))
				AND incident."status_id" IN (:activeStatusIds)
				AND incident."reporter_id" = :reporterId
				AND (:statusId IS NULL OR incident."status_id" = :statusId)
				AND (:categoryId IS NULL OR incident."category_id" = :categoryId)
				AND (NOT :uncategorized OR incident."category_id" IS NULL)
				AND (:priority IS NULL OR incident."priority" = :priority)
				AND (CAST(:createdAfter AS timestamptz) IS NULL OR incident."created_at" >= :createdAfter)
				AND (CAST(:createdBefore AS timestamptz) IS NULL OR incident."created_at" < :createdBefore)
			LIMIT :fetchLimit
			""", nativeQuery = true)
	List<IncidentMapProjection> findWithinBoundsForReporter(
			@Param("west") double west,
			@Param("south") double south,
			@Param("east") double east,
			@Param("north") double north,
			@Param("activeStatusIds") List<Integer> activeStatusIds,
			@Param("reporterId") int reporterId,
			@Param("statusId") Integer statusId,
			@Param("categoryId") Integer categoryId,
			@Param("uncategorized") boolean uncategorized,
			@Param("priority") String priority,
			@Param("createdAfter") java.time.Instant createdAfter,
			@Param("createdBefore") java.time.Instant createdBefore,
			@Param("fetchLimit") int fetchLimit);
}
