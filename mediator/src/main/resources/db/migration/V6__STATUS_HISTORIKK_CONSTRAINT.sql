ALTER TABLE status_historikk
ADD CONSTRAINT unique_person_dato UNIQUE (person_id, dato);
