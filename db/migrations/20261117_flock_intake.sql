ALTER TABLE public.flocks
    ADD COLUMN IF NOT EXISTS supplier VARCHAR(255),
    ADD COLUMN IF NOT EXISTS health_certificate VARCHAR(100),
    ADD COLUMN IF NOT EXISTS price_per_bird NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS initial_average_weight_g NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS vaccinated BOOLEAN;
