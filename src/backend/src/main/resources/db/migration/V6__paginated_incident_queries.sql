-- Stable date ordering and the common role/filter access paths for paginated lists.
CREATE INDEX "idx_incident_created_at_id"
    ON "Incident" ("created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_status_created_at_id"
    ON "Incident" ("status_id", "created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_category_created_at_id"
    ON "Incident" ("category_id", "created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_reporter_created_at_id"
    ON "Incident" ("reporter_id", "created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_assignee_created_at_id"
    ON "Incident" ("assignee_id", "created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_priority_rank_created_at_id"
    ON "Incident" (
        (CASE "priority"
            WHEN 'LOW' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'HIGH' THEN 3
            WHEN 'URGENT' THEN 4
            ELSE 0
        END) DESC,
        "created_at" DESC,
        "id" DESC
    );
CREATE INDEX "idx_incident_priority_created_at_id"
    ON "Incident" ("priority", "created_at" DESC, "id" DESC);

-- Replaced by indexes that also support ordered, paginated queries.
DROP INDEX "idx_incident_category_id";
DROP INDEX "idx_incident_reporter_id_created_at";
