CREATE TABLE "User" (
  "id" integer PRIMARY KEY,
  "nif" varchar,
  "name" varchar,
  "email" varchar,
  "phone" integer,
  "role_id" integer
);

CREATE TABLE "Role" (
  "id" integer PRIMARY KEY,
  "name" varchar
);

CREATE TABLE "UrbanAsset" (
  "id" integer PRIMARY KEY,
  "name" varchar,
  "asset_type_id" integer,
  "coordinates_id" integer,
  "geojson" JSONB
);

CREATE TABLE "AssetType" (
  "id" integer PRIMARY KEY,
  "name" varchar
);

CREATE TABLE "UrbanContext" (
  "id" integer PRIMARY KEY,
  "type_context_id" integer,
  "source" varchar,
  "json" JSONB,
  "observed_at" timestamp
);

CREATE TABLE "ContextType" (
  "id" integer PRIMARY KEY,
  "name" varchar
);

CREATE TABLE "Incident" (
  "id" integer PRIMARY KEY,
  "description" varchar,
  "category" varchar,
  "location" varchar,
  "priority" varchar,
  "status_id" integer,
  "user_id" integer,
  "urban_asset_id" integer,
  "urban_context_id" integer,
  "created_at" timestamp,
  "updated_at" timestamp
);

CREATE TABLE "Status" (
  "id" integer PRIMARY KEY,
  "name" varchar
);

CREATE TABLE "Attachment" (
  "id" integer PRIMARY KEY,
  "incident_id" integer,
  "file_url" varchar,
  "file_type" varchar,
  "created_at" timestamp
);

CREATE TABLE "FileType" (
  "id" integer PRIMARY KEY,
  "extension" varchar
);

CREATE TABLE "Assignment" (
  "id" integer PRIMARY KEY,
  "incident_id" integer,
  "assigned_user_id" integer,
  "department_id" varchar,
  "team_id" varchar,
  "assigned_at" timestamp
);

CREATE TABLE "Team" (
  "id" integer PRIMARY KEY,
  "name" varchar,
  "department_id" integer,
  "member_user_id" integer
);

CREATE TABLE "Department" (
  "id" integer PRIMARY KEY,
  "name" varchar,
  "team_id" integer
);

CREATE TABLE "StatusChange" (
  "id" integer PRIMARY KEY,
  "incident_id" integer,
  "change_user_id" integer,
  "previous_status" varchar,
  "new_status" varchar,
  "updates" varchar,
  "changed_at" timestamp
);

CREATE TABLE "ExternalObservation" (
  "id" integer PRIMARY KEY,
  "incident_id" integer,
  "observed_at" timestamp,
  "ingested_at" timestamp,
  "unit" varchar,
  "quality" varchar
);

CREATE TABLE "Notification" (
  "id" integer PRIMARY KEY,
  "user_id" integer,
  "incident_id" integer,
  "description" varchar,
  "sent_at" timestamp
);

CREATE TABLE "KnowledgeDocument" (
  "id" integer PRIMARY KEY,
  "title" varchar,
  "content" text,
  "version" varchar,
  "created_at" timestamp
);

ALTER TABLE "Incident" ADD FOREIGN KEY ("user_id") REFERENCES "User" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Incident" ADD FOREIGN KEY ("urban_asset_id") REFERENCES "UrbanAsset" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Incident" ADD FOREIGN KEY ("urban_context_id") REFERENCES "UrbanContext" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Incident" ADD FOREIGN KEY ("status_id") REFERENCES "Status" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Attachment" ADD FOREIGN KEY ("incident_id") REFERENCES "Incident" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Attachment" ADD FOREIGN KEY ("file_type") REFERENCES "FileType" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Assignment" ADD FOREIGN KEY ("incident_id") REFERENCES "Incident" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Assignment" ADD FOREIGN KEY ("assigned_user_id") REFERENCES "User" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Assignment" ADD FOREIGN KEY ("team_id") REFERENCES "Team" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Assignment" ADD FOREIGN KEY ("department_id") REFERENCES "Department" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Team" ADD FOREIGN KEY ("member_user_id") REFERENCES "User" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Team" ADD FOREIGN KEY ("department_id") REFERENCES "Department" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Department" ADD FOREIGN KEY ("team_id") REFERENCES "Team" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "StatusChange" ADD FOREIGN KEY ("incident_id") REFERENCES "Incident" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "StatusChange" ADD FOREIGN KEY ("change_user_id") REFERENCES "User" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ExternalObservation" ADD FOREIGN KEY ("incident_id") REFERENCES "Incident" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Notification" ADD FOREIGN KEY ("user_id") REFERENCES "User" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Notification" ADD FOREIGN KEY ("incident_id") REFERENCES "Incident" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "User" ADD FOREIGN KEY ("role_id") REFERENCES "Role" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "UrbanAsset" ADD FOREIGN KEY ("asset_type_id") REFERENCES "AssetType" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "UrbanContext" ADD FOREIGN KEY ("type_context_id") REFERENCES "ContextType" ("id") DEFERRABLE INITIALLY IMMEDIATE;
