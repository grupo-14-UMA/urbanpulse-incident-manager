-- Development and test staff accounts. This location is only enabled by Docker Compose and the
-- tests, never in a real deployment. The password of every account is "urbanpulse-dev".
INSERT INTO "User" ("name", "email", "password_hash", "role_id") VALUES
    ('Olga Operadora', 'operador@urbanpulse.test',
        '$2a$10$81bKAfcH6R4F6TNHkF9vJenOf9oSWT5NiW070c5HO/UyMJiI2QpP.', 2),
    ('Lucía Martín', 'tecnico1@urbanpulse.test',
        '$2a$10$81bKAfcH6R4F6TNHkF9vJenOf9oSWT5NiW070c5HO/UyMJiI2QpP.', 3),
    ('Javier Ruiz', 'tecnico2@urbanpulse.test',
        '$2a$10$81bKAfcH6R4F6TNHkF9vJenOf9oSWT5NiW070c5HO/UyMJiI2QpP.', 3)
ON CONFLICT ("email") DO NOTHING;
