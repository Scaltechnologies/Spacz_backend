-- RBI Grade B is one of the student app's standard "Aspiring for" exams but was missing from the seed.
INSERT INTO programs (code, name, description, category)
VALUES ('RBI_GRADE_B', 'RBI Grade B', 'Reserve Bank of India Grade B officer examination', 'Banking')
ON CONFLICT DO NOTHING;
