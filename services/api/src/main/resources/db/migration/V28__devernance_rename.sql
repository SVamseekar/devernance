-- The product was renamed to Devernance on 2026-10-18. V15, V17 and the demo seeder wrote the old name into rows
-- that already exist; this rewrites only those rows, matched by their fixed ids and old values.

update obligation_rules
   set description = 'Baseline suggested governance for any AI system registered in Devernance (always mapped for inventory completeness).'
 where id in ('a1500001-0000-4000-8000-000000000007', 'a1700001-0000-4000-8000-000000000007')
   and description like '%assurance OS%';

update tenants set name = 'Devernance demo'
 where id = '00000000-0000-0000-0000-0000000000d0' and name = 'Assurance OS demo';

update users set email = 'demo@devernance.invalid'
 where id = '00000000-0000-0000-0000-0000000000d1' and email = 'demo@assurance-os.invalid';
