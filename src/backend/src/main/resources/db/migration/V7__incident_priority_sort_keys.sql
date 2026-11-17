-- This stored generated column backfills every incident and can rewrite the table.
-- Deploy during the maintenance window documented in docs/deployment.md.
ALTER TABLE "Incident"
    ADD COLUMN "priority_rank" integer GENERATED ALWAYS AS (
        CASE "priority"
            WHEN 'LOW' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'HIGH' THEN 3
            WHEN 'URGENT' THEN 4
            ELSE 5
        END
    ) STORED,
    ADD COLUMN "priority_rank_desc" integer GENERATED ALWAYS AS (
        CASE "priority"
            WHEN 'LOW' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'HIGH' THEN 3
            WHEN 'URGENT' THEN 4
            ELSE 0
        END
    ) STORED;
