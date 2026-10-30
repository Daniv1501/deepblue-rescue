-- ============================================================
-- Datos de prueba para el laboratorio de frontend (DeepBlue Rescue)
--
-- El backend solo trae el catálogo de expertise (V2). Este script
-- agrega casos, animales, especialistas y un tratamiento para poder
-- probar la interfaz. Se puede ejecutar varias veces sin duplicar.
--
-- Ejecutar con el backend ya iniciado una vez (para que Flyway cree
-- las tablas):
--   psql -h localhost -U postgres -d deepblue -f datos-prueba.sql
-- ============================================================

INSERT INTO rescue_centers (code, name, city) VALUES
    ('DB-CAR', 'DeepBlue Caribbean', 'Santa Marta')
ON CONFLICT (code) DO NOTHING;

INSERT INTO rescue_cases (case_code, rescue_date, rescue_location, status, rescue_center_id)
SELECT v.case_code, v.rescue_date::date, v.rescue_location, v.status, c.id
FROM (VALUES
    ('RES-001', '2026-08-20', 'Bahia Concha',  'ADMITTED'),
    ('RES-002', '2026-08-25', 'Taganga',       'ADMITTED'),
    ('RES-003', '2026-08-10', 'Playa Blanca',  'UNDER_EVALUATION'),
    ('RES-004', '2026-07-30', 'Isla Fuerte',   'IN_REHABILITATION'),
    ('RES-005', '2026-06-15', 'Rodadero',      'RELEASED')
) AS v(case_code, rescue_date, rescue_location, status)
CROSS JOIN rescue_centers c
WHERE c.code = 'DB-CAR'
ON CONFLICT (case_code) DO NOTHING;

INSERT INTO animals (animal_code, common_name, scientific_name, sex, rescue_case_id)
SELECT v.animal_code, v.common_name, v.scientific_name, v.sex, rc.id
FROM (VALUES
    ('AN-001', 'Green Sea Turtle',       'Chelonia mydas',          'FEMALE',  'RES-001'),
    ('AN-002', 'West Indian Manatee',    'Trichechus manatus',      'MALE',    'RES-002'),
    ('AN-003', 'Brown Pelican',          'Pelecanus occidentalis',  'UNKNOWN', 'RES-003'),
    ('AN-004', 'Hawksbill Sea Turtle',   'Eretmochelys imbricata',  'FEMALE',  'RES-004'),
    ('AN-005', 'Bottlenose Dolphin',     'Tursiops truncatus',      'MALE',    'RES-005')
) AS v(animal_code, common_name, scientific_name, sex, case_code)
JOIN rescue_cases rc ON rc.case_code = v.case_code
ON CONFLICT (animal_code) DO NOTHING;

INSERT INTO specialists (professional_code, first_name, last_name, email, active) VALUES
    ('SPEC-001', 'Elena',  'Vargas', 'elena.vargas@deepblue.org',  TRUE),
    ('SPEC-002', 'Andres', 'Mejia',  'andres.mejia@deepblue.org',  FALSE)
ON CONFLICT (professional_code) DO NOTHING;

-- Un tratamiento previo para ver la tabla con datos (AN-004)
INSERT INTO treatments (animal_id, specialist_id, performed_at, type, description)
SELECT a.id, s.id, TIMESTAMP '2026-07-31 09:00', 'WOUND_CARE', 'Cleaning of carapace fracture.'
FROM animals a, specialists s
WHERE a.animal_code = 'AN-004' AND s.professional_code = 'SPEC-001'
  AND NOT EXISTS (
      SELECT 1 FROM treatments t
      WHERE t.animal_id = a.id AND t.performed_at = TIMESTAMP '2026-07-31 09:00'
  );

-- Resumen para anotar en el laboratorio:
--   caseCode:        RES-001 (ADMITTED)
--   animalCode:      AN-001
--   specialistCode:  SPEC-001 (activo)  /  SPEC-002 (inactivo -> 409)
