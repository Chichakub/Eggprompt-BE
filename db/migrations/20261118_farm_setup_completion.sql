BEGIN;

ALTER TABLE public.farms
    ADD COLUMN IF NOT EXISTS setup_completed_at TIMESTAMP;

-- Existing farms with flock history have already completed initial setup.
UPDATE public.farms farm
SET setup_completed_at = CURRENT_TIMESTAMP
WHERE farm.setup_completed_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM public.houses house
      JOIN public.flocks flock ON flock.house_id = house.house_id
      WHERE house.farm_id = farm.farm_id
  );

COMMIT;
