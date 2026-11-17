\set ON_ERROR_STOP on

BEGIN;
SET LOCAL synchronous_commit = off;

CREATE TEMP TABLE synthetic_incident_config ON COMMIT DROP AS
SELECT CAST(:'incident_count' AS integer) AS incident_count,
       statement_timestamp() AS generated_at;

DO $$
DECLARE
    configured_count integer;
BEGIN
    SELECT incident_count INTO configured_count FROM synthetic_incident_config;
    IF configured_count < 1 OR configured_count > 5000000 THEN
        RAISE EXCEPTION 'incident_count must be between 1 and 5000000 (received %)', configured_count;
    END IF;
END $$;

CREATE TEMP TABLE synthetic_incident_actors ON COMMIT DROP AS
SELECT (SELECT id FROM "User" WHERE role_id = 2 ORDER BY id LIMIT 1) AS operator_id,
       ARRAY(SELECT id FROM "User" WHERE role_id = 3 ORDER BY id) AS technician_ids,
       ARRAY(SELECT id FROM "Category" ORDER BY id) AS category_ids;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM synthetic_incident_actors WHERE operator_id IS NOT NULL) THEN
        RAISE EXCEPTION 'The development operator is missing; start the backend with Docker Compose so db/dev-seed is applied.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM synthetic_incident_actors WHERE cardinality(technician_ids) > 0) THEN
        RAISE EXCEPTION 'Development technicians are missing; start the backend with Docker Compose so db/dev-seed is applied.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM synthetic_incident_actors WHERE cardinality(category_ids) > 0) THEN
        RAISE EXCEPTION 'No incident categories exist; start the backend so Flyway migrations are applied.';
    END IF;
END $$;

INSERT INTO "User" ("name", "email", "password_hash", "role_id")
SELECT 'Ciudadano sintético ' || lpad(reporter_number::text, 6, '0'),
       'ciudadano.sintetico.' || lpad(reporter_number::text, 6, '0') || '@urbanpulse.test',
       '$2a$10$81bKAfcH6R4F6TNHkF9vJenOf9oSWT5NiW070c5HO/UyMJiI2QpP.',
       1
FROM synthetic_incident_config
CROSS JOIN LATERAL generate_series(
    1,
    LEAST(1000, GREATEST(1, CEIL(incident_count / 1000.0)::integer))
) AS generated_reporter(reporter_number)
ON CONFLICT ("email") DO NOTHING;

CREATE TEMP TABLE synthetic_incident_reporters ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY id)::integer AS reporter_number,
       id AS reporter_id
FROM "User"
WHERE role_id = 1
  AND email LIKE 'ciudadano.sintetico.%@urbanpulse.test';

CREATE TEMP TABLE synthetic_incident_rows ON COMMIT DROP AS
WITH
reporter_count AS (
    SELECT count(*)::integer AS value FROM synthetic_incident_reporters
),
clusters(cluster_number, latitude, longitude) AS (
    VALUES
        (1, 36.7214::double precision, -4.4214::double precision), -- Centro
        (2, 36.7113, -4.4258),                                    -- El Perchel
        (3, 36.7025, -4.4296),                                    -- Huelin
        (4, 36.6948, -4.4480),                                    -- La Luz
        (5, 36.7168, -4.4744),                                    -- Teatinos
        (6, 36.7375, -4.4215),                                    -- Ciudad Jardín
        (7, 36.7310, -4.4340),                                    -- Bailén-Miraflores
        (8, 36.7225, -4.3560),                                    -- El Palo
        (9, 36.7212, -4.3830),                                    -- Pedregalejo
        (10, 36.7355, -4.4810),                                   -- Puerto de la Torre
        (11, 36.6658, -4.4910),                                   -- Churriana
        (12, 36.6720, -4.5570),                                   -- Campanillas
        (13, 36.7250, -4.4290)                                    -- La Trinidad
),
sequence_values AS (
    SELECT generated_number::bigint AS sequence_number,
           (mod(generated_number - 1, 100))::integer AS lifecycle_bucket,
           (mod(generated_number - 1, 13) + 1)::integer AS cluster_number,
           (mod(generated_number::bigint * 48271 + 12345, 2147483647)::double precision / 2147483647.0) AS latitude_offset,
           (mod(generated_number::bigint * 69621 + 104729, 2147483647)::double precision / 2147483647.0) AS longitude_offset,
           (mod(generated_number::bigint * 104729 + 130363, 2147483647)::double precision / 2147483647.0) AS age_fraction,
           (mod(generated_number - 1, (SELECT value FROM reporter_count)) + 1)::integer AS reporter_number,
           (mod(generated_number - 1, cardinality(actors.category_ids)) + 1)::integer AS category_number,
           actors.operator_id,
           actors.technician_ids,
           actors.category_ids,
           config.generated_at
    FROM synthetic_incident_config AS config
    CROSS JOIN synthetic_incident_actors AS actors
    CROSS JOIN LATERAL generate_series(1, config.incident_count) AS generated_incident(generated_number)
),
classified AS (
    SELECT sequence_values.*,
           CASE
               WHEN lifecycle_bucket < 10 THEN 1
               WHEN lifecycle_bucket < 20 THEN 2
               WHEN lifecycle_bucket < 30 THEN 3
               WHEN lifecycle_bucket < 45 THEN 4
               WHEN lifecycle_bucket < 60 THEN 5
               ELSE 6
           END AS status_id
    FROM sequence_values
),
timed AS (
    SELECT classified.*,
           CASE status_id
               WHEN 1 THEN 0.01 + age_fraction * 365.0
               WHEN 2 THEN 1.0 + age_fraction * 365.0
               WHEN 3 THEN 1.0 + age_fraction * 365.0
               WHEN 4 THEN 2.0 + age_fraction * 365.0
               WHEN 5 THEN 3.0 + age_fraction * 180.0
               ELSE 7.0 + age_fraction * 730.0
           END AS age_days,
           CASE
               WHEN status_id IN (2, 4, 5, 6) AND mod(sequence_number, 5) <> 0
                   THEN (ARRAY['LOW', 'MEDIUM', 'HIGH', 'URGENT'])[(mod(sequence_number - 1, 4) + 1)::integer]
               ELSE NULL
           END AS priority,
           CASE
               WHEN status_id IN (4, 5, 6)
                   THEN technician_ids[(mod(sequence_number, cardinality(technician_ids)) + 1)::integer]
               ELSE NULL
           END AS assignee_id,
           CASE
               WHEN mod(sequence_number, 7) = 0 THEN NULL
               ELSE category_ids[category_number]
           END AS category_id,
           reporter_id
    FROM classified
    JOIN synthetic_incident_reporters AS reporters USING (reporter_number)
),
dated AS (
    SELECT timed.*,
           generated_at - age_days * interval '1 day' AS created_at,
           CASE status_id
               WHEN 1 THEN generated_at - age_days * interval '1 day'
               WHEN 2 THEN generated_at - age_days * interval '1 day' + age_days * 0.8 * interval '1 day'
               WHEN 3 THEN generated_at - age_days * interval '1 day' + age_days * 0.8 * interval '1 day'
               WHEN 4 THEN generated_at - age_days * interval '1 day' + age_days * 0.8 * interval '1 day'
               WHEN 5 THEN generated_at - age_days * interval '1 day' + age_days * 0.75 * interval '1 day'
               ELSE generated_at - age_days * interval '1 day' + age_days * 0.8 * interval '1 day'
           END AS updated_at
    FROM timed
)
SELECT nextval(pg_get_serial_sequence('"Incident"', 'id'))::integer AS incident_id,
       sequence_number,
       status_id,
       category_id,
       reporter_id,
       assignee_id,
       priority,
       CASE WHEN priority IS NULL THEN NULL ELSE 'Prioridad sintética asignada durante la validación.' END AS priority_justification,
       cluster.latitude + (latitude_offset - 0.5) * 0.0012 AS latitude,
       cluster.longitude + (longitude_offset - 0.5) * 0.0016 AS longitude,
       created_at,
       updated_at,
       CASE status_id
           WHEN 1 THEN 0
           WHEN 2 THEN 1
           WHEN 3 THEN 1
           WHEN 4 THEN 2
           WHEN 5 THEN 3
           ELSE 4
       END AS version,
       operator_id
FROM dated
JOIN clusters AS cluster USING (cluster_number);

INSERT INTO "Incident" (
    id, title, description, category_id, location, status_id, created_at, updated_at,
    reporter_id, priority, priority_justification, assignee_id, version
)
SELECT incident_id,
       CASE mod(sequence_number, 8)
           WHEN 0 THEN 'Bache en la calzada'
           WHEN 1 THEN 'Luminaria fuera de servicio'
           WHEN 2 THEN 'Acumulación de residuos'
           WHEN 3 THEN 'Acerado deteriorado'
           WHEN 4 THEN 'Semáforo averiado'
           WHEN 5 THEN 'Rama caída en la vía pública'
           WHEN 6 THEN 'Mobiliario urbano dañado'
           ELSE 'Señalización deteriorada'
       END,
       CASE mod(sequence_number, 4)
           WHEN 0 THEN 'Incidencia sintética para pruebas locales de consulta y rendimiento.'
           WHEN 1 THEN 'Se observa un problema que requiere revisión del servicio municipal correspondiente.'
           WHEN 2 THEN 'Aviso generado de forma reproducible para poblar la base de datos de desarrollo.'
           ELSE 'La ubicación y el estado forman parte del conjunto sintético de Málaga.'
       END,
       category_id,
       ST_SetSRID(ST_MakePoint(longitude, latitude), 4326),
       status_id,
       created_at,
       updated_at,
       reporter_id,
       priority,
       priority_justification,
       assignee_id,
       version
FROM synthetic_incident_rows;

INSERT INTO "StatusChange" (
    incident_id, previous_status_id, new_status_id, changed_by_id, changed_at, note
)
SELECT incident_id, NULL, 1, reporter_id, created_at, NULL
FROM synthetic_incident_rows
UNION ALL
SELECT incident_id, 1, 2, operator_id,
       CASE status_id
           WHEN 2 THEN updated_at
           WHEN 4 THEN created_at + (updated_at - created_at) * 0.5
           WHEN 5 THEN created_at + (updated_at - created_at) / 3.0
           ELSE created_at + (updated_at - created_at) * 0.25
       END,
       NULL
FROM synthetic_incident_rows
WHERE status_id IN (2, 4, 5, 6)
UNION ALL
SELECT incident_id, 1, 3, operator_id, updated_at, 'Motivo sintético de rechazo.'
FROM synthetic_incident_rows
WHERE status_id = 3
UNION ALL
SELECT incident_id, 2, 4, operator_id,
       CASE status_id
           WHEN 4 THEN updated_at
           WHEN 5 THEN created_at + (updated_at - created_at) * (2.0 / 3.0)
           ELSE created_at + (updated_at - created_at) * 0.5
       END,
       NULL
FROM synthetic_incident_rows
WHERE status_id IN (4, 5, 6)
UNION ALL
SELECT incident_id, 4, 5, assignee_id,
       CASE status_id
           WHEN 5 THEN updated_at
           ELSE created_at + (updated_at - created_at) * 0.75
       END,
       NULL
FROM synthetic_incident_rows
WHERE status_id IN (5, 6)
UNION ALL
SELECT incident_id, 5, 6, assignee_id, updated_at, 'Reparación sintética completada.'
FROM synthetic_incident_rows
WHERE status_id = 6;

COMMIT;
