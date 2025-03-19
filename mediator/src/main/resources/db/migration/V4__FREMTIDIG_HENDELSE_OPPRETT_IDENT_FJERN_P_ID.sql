-- Legger til ident i fremtidig_hendelse
ALTER TABLE fremtidig_hendelse ADD COLUMN ident VARCHAR(255);

-- Populerer ident med identer fra person-tabellen
UPDATE fremtidig_hendelse fh
SET ident = p.ident
FROM person p
WHERE fh.person_id = p.id;

-- Setter ident til å ikke kunne være null
ALTER TABLE fremtidig_hendelse ALTER COLUMN ident SET NOT NULL;

-- Fjerner fremmednøkkel for person_id
ALTER TABLE fremtidig_hendelse DROP CONSTRAINT fremtidig_hendelse_person_id_fkey;

-- Fjerner person_id-kolonnen
ALTER TABLE fremtidig_hendelse DROP COLUMN person_id;