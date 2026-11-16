-- Supports the technician work queue: filter by assignee and active status,
-- then return the newest incidents first without scanning resolved work.
CREATE INDEX "idx_incident_active_assignee_created_at"
    ON "Incident" ("assignee_id", "created_at" DESC, "id" DESC)
    WHERE "status_id" IN (1, 2, 4, 5);
