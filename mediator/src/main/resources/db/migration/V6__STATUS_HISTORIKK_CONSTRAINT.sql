DELETE FROM status_historikk a
    USING status_historikk b
WHERE a.ctid < b.ctid
  AND a.person_id = b.person_id
  AND a.dato = b.dato;


ALTER TABLE status_historikk
ADD CONSTRAINT unique_person_dato UNIQUE (person_id, dato);
