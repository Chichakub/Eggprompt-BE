-- Minimal prerequisites for the live Cypress Excel-41 suite.
-- Apply deliberately in the selected QA database using its SQL editor.
-- INSERT ONLY: never clears tables, resets existing passwords or revives deleted accounts.
-- Existing conflicting/inactive fixtures abort the transaction for review.
BEGIN;
SET LOCAL search_path = public, extensions;
DO $fixtures$
DECLARE
  fixture record;
  i integer;
  page_id text;
BEGIN
  FOR fixture IN SELECT * FROM (VALUES
    ('PM-EXAM-A','Cypress QA Farm A'),
    ('PM-EXAM-B','Cypress QA Farm B')
  ) AS f(id,name)
  LOOP
    IF EXISTS (SELECT 1 FROM public.farms WHERE farm_id=fixture.id AND (deleted_at IS NOT NULL OR NOT is_active)) THEN
      RAISE EXCEPTION 'Fixture farm % exists but is inactive/deleted. Review it manually.',fixture.id;
    END IF;
    INSERT INTO public.farms(farm_id,farm_name,address,tax_id,start_date,owner_name,phone_number,livestock_reg_number,subscription_status,is_active)
    SELECT fixture.id,fixture.name,'Cypress QA Bangkok','0105565012345','2024-01-01','Cypress QA Owner','0812345678',fixture.id||'-REG','ACTIVE',true
    WHERE NOT EXISTS (SELECT 1 FROM public.farms WHERE farm_id=fixture.id);
    INSERT INTO public.houses(farm_id,house_name,width_m,length_m,capacity,status,last_built_or_renovated_date,created_at)
    SELECT fixture.id,fixture.id||'-BASE',10,20,100,'ACTIVE','2024-01-01',now()
    WHERE NOT EXISTS (SELECT 1 FROM public.houses WHERE farm_id=fixture.id AND house_name=fixture.id||'-BASE' AND deleted_at IS NULL);
  END LOOP;
  FOR fixture IN SELECT * FROM (VALUES
    ('pm.admin.a','Farm Admin','PM-EXAM-A'),
    ('pm.staff.a','Farm Staff','PM-EXAM-A'),
    ('pm.finance.a','Finance Staff','PM-EXAM-A'),
    ('pm.admin.b','Farm Admin','PM-EXAM-B')
  ) AS u(name,role,farm)
  LOOP
    IF EXISTS (SELECT 1 FROM public.users WHERE username=fixture.name AND (deleted_at IS NOT NULL OR NOT is_active OR farm_id IS DISTINCT FROM fixture.farm OR user_role<>fixture.role)) THEN
      RAISE EXCEPTION 'Fixture user % conflicts with an existing account. No password was changed.',fixture.name;
    END IF;
    INSERT INTO public.users(farm_id,username,email,password_hash,full_name,user_role,is_active,must_change_password,onboarding_required,auth_version,created_at)
    SELECT fixture.farm,fixture.name,fixture.name||'@example.invalid',crypt('Postman@123',gen_salt('bf',12)),
           'Cypress QA Fixture',fixture.role,true,false,false,0,now()
    WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE username=fixture.name);
  END LOOP;
  FOR i IN 1..11 LOOP
    page_id := 'R1-PAGE-'||lpad(i::text,2,'0');
    IF EXISTS (SELECT 1 FROM public.farms WHERE farm_id=page_id AND deleted_at IS NOT NULL) THEN
      RAISE EXCEPTION 'Pagination fixture % is deleted. Review before reuse.',page_id;
    END IF;
    INSERT INTO public.farms(farm_id,farm_name,address,tax_id,start_date,owner_name,phone_number,livestock_reg_number,subscription_status,is_active)
    SELECT page_id,'R1 Postman '||lpad(i::text,2,'0'),'Cypress QA pagination','09990000000'||lpad(i::text,2,'0'),
           '2024-01-01','Cypress QA Owner','0800000000','R1-REG-'||i,
           CASE WHEN i=11 THEN 'SUSPENDED' ELSE 'ACTIVE' END,i<>11
    WHERE NOT EXISTS (SELECT 1 FROM public.farms WHERE farm_id=page_id);
  END LOOP;
END $fixtures$;
COMMIT;
SELECT username,user_role,farm_id,is_active FROM public.users
WHERE username IN ('pm.admin.a','pm.staff.a','pm.finance.a','pm.admin.b') ORDER BY username;
