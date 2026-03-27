-- Keycloak now manages passwords. Local password_hash is no longer required.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash SET DEFAULT 'KEYCLOAK_MANAGED';
