-- Apply to the database used by the backend before deploying farm inspection.
-- Existing farms remain active; existing data is retained.
ALTER TABLE public.farms
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);
