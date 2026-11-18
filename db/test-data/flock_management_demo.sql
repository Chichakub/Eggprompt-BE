-- Run in Supabase SQL Editor after replacing v_farm_id below.
-- Adds only clearly named DEMO records to an existing farm. Does not reset tables.
-- Supabase must also contain users.onboarding_required and farms.setup_completed_at for the setup flow.
BEGIN;
ALTER TABLE public.flocks
    ADD COLUMN IF NOT EXISTS supplier VARCHAR(255),
    ADD COLUMN IF NOT EXISTS health_certificate VARCHAR(100),
    ADD COLUMN IF NOT EXISTS price_per_bird NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS initial_average_weight_g NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS vaccinated BOOLEAN;

DO $$
DECLARE
    v_farm_id TEXT := 'REPLACE_WITH_YOUR_FARM_ID';
    v_breed_id INT;
    v_house_id INT;
    demo RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.farms WHERE farm_id=v_farm_id AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'Replace v_farm_id with an existing farm_id before running this script';
    END IF;

    SELECT breed_id INTO v_breed_id FROM public.breeds
    WHERE (breed_code='HYL-BRN' OR lower(trim(breed_name))='hy-line brown') AND is_active AND deleted_at IS NULL
    ORDER BY breed_id LIMIT 1;
    IF v_breed_id IS NULL THEN
        INSERT INTO public.breeds(breed_name,breed_code,description,is_active)
        VALUES ('Hy-Line Brown','HYL-BRN','สายพันธุ์ไก่ไข่ Hy-Line Brown',TRUE)
        RETURNING breed_id INTO v_breed_id;
    ELSIF NOT EXISTS (SELECT 1 FROM public.breeds WHERE breed_id=v_breed_id AND is_active) THEN
        RAISE EXCEPTION 'The DEMO breed is inactive; choose a different test breed or enable it deliberately';
    END IF;

    FOR demo IN SELECT * FROM (VALUES
      ('DEMO-FLOCK-01','โรงเรือน DEMO ฝูงไก่ 01',1000,18,42,140.00,1100.00,'ACTIVE',TRUE),
      ('DEMO-FLOCK-02','โรงเรือน DEMO ฝูงไก่ 02',1500,20,140,145.00,1200.00,'BELOW_STANDARD',TRUE),
      ('DEMO-FLOCK-03','โรงเรือน DEMO ฝูงไก่ 03',1200,70,56,130.00,1600.00,'NEAR_RETIRE',FALSE)
    ) AS data(code,house_name,quantity,age_weeks,days_ago,price,weight,status,vaccinated)
    LOOP
        -- Skip existing live DEMO codes so repeated runs do not overwrite edits.
        IF EXISTS (SELECT 1 FROM public.flocks f JOIN public.houses h USING(house_id)
                   WHERE f.flock_code=demo.code AND f.deleted_at IS NULL AND h.farm_id<>v_farm_id) THEN
            RAISE EXCEPTION 'DEMO flock code % belongs to a different farm; use different test codes',demo.code;
        END IF;
        IF EXISTS (SELECT 1 FROM public.flocks WHERE flock_code=demo.code AND deleted_at IS NULL) THEN
            CONTINUE;
        END IF;
        SELECT house_id INTO v_house_id FROM public.houses
        WHERE farm_id=v_farm_id AND house_name=demo.house_name AND deleted_at IS NULL FOR UPDATE;
        IF v_house_id IS NULL THEN
            INSERT INTO public.houses(farm_id,house_name,house_type,opening_date,width_m,length_m,capacity,status)
            VALUES(v_farm_id,demo.house_name,'CLOSED',CURRENT_DATE-200,10,30,2000,'ACTIVE')
            RETURNING house_id INTO v_house_id;
        END IF;
        IF EXISTS (SELECT 1 FROM public.flocks WHERE house_id=v_house_id AND deleted_at IS NULL AND status<>'RETIRED') THEN
            RAISE EXCEPTION 'DEMO house % already contains another live flock; no records were overwritten',v_house_id;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.houses WHERE house_id=v_house_id AND status='ACTIVE' AND capacity>=demo.quantity) THEN
            RAISE EXCEPTION 'DEMO house % is inactive or too small',v_house_id;
        END IF;
        INSERT INTO public.flocks(flock_code,house_id,breed_id,received_date,initial_age_weeks,
            initial_count,current_count,status,supplier,health_certificate,price_per_bird,initial_average_weight_g,vaccinated)
        VALUES(demo.code,v_house_id,v_breed_id,CURRENT_DATE-demo.days_ago,demo.age_weeks,
            demo.quantity,demo.quantity,demo.status,'ผู้จำหน่ายตัวอย่าง (ทดสอบ)','DEMO-CERT-'||demo.code,
            demo.price,demo.weight,demo.vaccinated);
    END LOOP;
    INSERT INTO public.houses(farm_id,house_name,house_type,opening_date,width_m,length_m,capacity,status)
    SELECT v_farm_id,'โรงเรือน DEMO ว่างสำหรับเพิ่มฝูง','CLOSED',CURRENT_DATE-200,10,30,2000,'ACTIVE'
    WHERE NOT EXISTS (SELECT 1 FROM public.houses WHERE farm_id=v_farm_id
                      AND house_name='โรงเรือน DEMO ว่างสำหรับเพิ่มฝูง' AND deleted_at IS NULL);
END $$;
COMMIT;

SELECT f.flock_code,h.farm_id,h.house_name,b.breed_name,f.initial_count,f.current_count,
       f.received_date,f.status,f.supplier,f.price_per_bird,f.vaccinated
FROM public.flocks f JOIN public.houses h USING(house_id) JOIN public.breeds b USING(breed_id)
WHERE f.flock_code IN ('DEMO-FLOCK-01','DEMO-FLOCK-02','DEMO-FLOCK-03') AND f.deleted_at IS NULL
ORDER BY f.flock_code;
