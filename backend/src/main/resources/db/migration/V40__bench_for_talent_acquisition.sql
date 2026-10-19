-- V40: Bench Management is on for Talent Acquisition by default (Expansion Phase X6).
--
-- V34 switched Bench Management on for the department codes HR and TALENT_ACQUISITION, but V2 seeds Talent
-- Acquisition with the code TA, so only HR got it. This adds the missing default. A department that already has a
-- bench row (a Super Admin switched it on or off in Admin settings) keeps it.

INSERT INTO department_modules (module_key, department_id, enabled)
SELECT 'bench', d.id, TRUE
FROM departments d
WHERE d.code = 'TA'
  AND NOT EXISTS (SELECT 1 FROM department_modules m WHERE m.module_key = 'bench' AND m.department_id = d.id);
