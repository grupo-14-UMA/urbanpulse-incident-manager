-- Keep index creation in a separate migration from the table rewrite in V7.
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
    ON "Incident" ("priority_rank_desc" DESC, "created_at" DESC, "id" DESC);
CREATE INDEX "idx_incident_priority_rank_asc_created_at_id"
    ON "Incident" ("priority_rank" ASC, "created_at" ASC, "id" ASC);
CREATE INDEX "idx_incident_priority_created_at_id"
    ON "Incident" ("priority", "created_at" DESC, "id" DESC);

-- Replaced by indexes that also support ordered, paginated queries.
DROP INDEX "idx_incident_category_id";
DROP INDEX "idx_incident_reporter_id_created_at";
