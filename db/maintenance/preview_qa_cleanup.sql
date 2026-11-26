-- READ-ONLY PREVIEW. Run in Supabase SQL Editor on the database used by the VM.
-- No DELETE, UPDATE, TRUNCATE, or sequence reset. Names are candidates for review,
-- not proof that a record can be removed. Keep real records even if a name matches.

-- 1. Farm candidates, including counts of dependent records.
SELECT f.farm_id, f.farm_name,
       (SELECT count(*) FROM public.users u WHERE u.farm_id=f.farm_id) AS linked_users,
       (SELECT count(*) FROM public.houses h WHERE h.farm_id=f.farm_id) AS linked_houses,
       (SELECT count(*) FROM public.flocks k JOIN public.houses h ON h.house_id=k.house_id
          WHERE h.farm_id=f.farm_id) AS linked_flocks,
       CASE WHEN f.farm_id IN ('PM-EXAM-A','PM-EXAM-B') THEN 'Base Postman fixture: required by live tests'
            WHEN f.farm_id ~ '^R1-PAGE-[0-9]{2}$' THEN 'Pagination fixture: required by Postman pagination tests'
            ELSE 'Generated test farm: review before removal' END AS review_note
FROM public.farms f
WHERE f.farm_id IN ('PM-EXAM-A','PM-EXAM-B')
   OR f.farm_id ~ '^R1-PAGE-[0-9]{2}$'
   OR f.farm_name ~ '^QA (Mail )?Farm [0-9]+$'
   OR f.farm_name ~ '^CY-FARM-[0-9]+$'
ORDER BY f.farm_id;

-- 2. Account candidates. Super Admin accounts are excluded regardless of name.
SELECT u.user_id, u.username, u.user_role, u.farm_id,
       CASE WHEN u.username IN ('pm.admin.a','pm.staff.a','pm.finance.a','pm.admin.b','pm.r1.password')
            THEN 'Base test login: removing it breaks corresponding tests'
            ELSE 'Generated test account: review before removal' END AS review_note
FROM public.users u
WHERE u.user_role <> 'Super Admin'
  AND (u.username IN ('pm.admin.a','pm.staff.a','pm.finance.a','pm.admin.b','pm.r1.password')
       OR u.username ~ '^qa_(staff|hold|owner)_[0-9]+$'
       OR u.username ~ '^cy[.]owner[.][0-9]+$')
ORDER BY u.user_id;

-- 3. Repeated houses created by test runs. Include flock counts so dependent
-- feed/egg/health histories are considered before any removal is planned.
SELECT h.house_id, h.farm_id, h.house_name,
       (SELECT count(*) FROM public.flocks k WHERE k.house_id=h.house_id) AS linked_flocks
FROM public.houses h
WHERE h.house_name ~ '^(A-H01|B-H01|A-H02|A-INTAKE|CY-HOUSE)-[0-9]+$'
ORDER BY h.farm_id,h.house_id;

-- 4. All foreign keys in public: use this to plan deletion order from the actual
-- deployed schema. Do not use CASCADE to remove unknown dependent data.
SELECT tc.table_name AS child_table, kcu.column_name AS child_column,
       ccu.table_name AS parent_table, ccu.column_name AS parent_column,
       rc.delete_rule
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
 ON tc.constraint_catalog=kcu.constraint_catalog
 AND tc.constraint_schema=kcu.constraint_schema AND tc.constraint_name=kcu.constraint_name
JOIN information_schema.referential_constraints rc
 ON tc.constraint_catalog=rc.constraint_catalog
 AND tc.constraint_schema=rc.constraint_schema AND tc.constraint_name=rc.constraint_name
JOIN information_schema.constraint_column_usage ccu
 ON rc.unique_constraint_catalog=ccu.constraint_catalog
 AND rc.unique_constraint_schema=ccu.constraint_schema AND rc.unique_constraint_name=ccu.constraint_name
WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
ORDER BY child_table,child_column;
