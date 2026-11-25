-- Additional isolated fixtures for the workbook-aligned suite.
-- Run once AFTER the original setup-fixtures.sql in the chosen database.
-- Creates 11 farms for exact 10+1 pagination / active+suspended filtering,
-- and a dedicated password-change account. No existing records are updated.
BEGIN;
SET LOCAL search_path = public, extensions;
DO $extra$
DECLARE i integer;
BEGIN
 IF NOT EXISTS (SELECT 1 FROM public.farms WHERE farm_id='PM-EXAM-A') THEN
  RAISE EXCEPTION 'Run original setup-fixtures.sql first.';
 END IF;
 IF EXISTS (SELECT 1 FROM public.farms WHERE farm_id LIKE 'R1-PAGE-%')
    OR EXISTS (SELECT 1 FROM public.users WHERE username='pm.r1.password') THEN
  RAISE EXCEPTION 'Release 1 extra fixtures already exist. Reuse them, do not run setup twice.';
 END IF;
 FOR i IN 1..11 LOOP
  INSERT INTO public.farms (farm_id,farm_name,address,tax_id,start_date,owner_name,phone_number,livestock_reg_number,subscription_status,is_active)
  VALUES ('R1-PAGE-'||lpad(i::text,2,'0'),'R1 Postman '||lpad(i::text,2,'0'),
          'Release 1 pagination test address','09990000000'||lpad(i::text,2,'0'),
          '2024-01-01','QA Owner','0800000000','R1-REG-'||i,
          CASE WHEN i=11 THEN 'SUSPENDED' ELSE 'ACTIVE' END,i<>11);
 END LOOP;
 INSERT INTO public.users (farm_id,username,email,password_hash,full_name,user_role,is_active,must_change_password,onboarding_required,auth_version,email_verification_hash,email_verification_expires_at)
 VALUES ('PM-EXAM-A','pm.r1.password','pm.r1.password@example.invalid',
         crypt('Postman@123',gen_salt('bf',12)),'QA Password Account','Farm Admin',true,false,false,0,NULL,NULL);
END $extra$;
COMMIT;
SELECT farm_id,farm_name,is_active FROM public.farms WHERE farm_id LIKE 'R1-PAGE-%' ORDER BY farm_id;
