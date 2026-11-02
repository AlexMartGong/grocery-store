-- =====================================================================
-- Migration 2026-11-01: store-user refresh tokens (persistent sessions)
-- =====================================================================
-- script.sql is an initdb script (runs only on an empty volume), so databases
-- provisioned before this change need this applied by hand BEFORE deploying the
-- app version that maps RefreshToken (ddl-auto=validate fails boot otherwise):
--
--   docker exec -i ventacore-db psql -U postgres -d grocerystore -v ON_ERROR_STOP=1 \
--     < src/main/resources/migrations/2026-11-01-refresh-tokens.sql
--
-- Idempotent (IF NOT EXISTS everywhere) and transactional: safe to re-run.
-- Mirrors the refresh_tokens DDL in script.sql (MODULE 2).

BEGIN;

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    store_id    UUID        NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID                 REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_store ON refresh_tokens (store_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id);

COMMIT;
