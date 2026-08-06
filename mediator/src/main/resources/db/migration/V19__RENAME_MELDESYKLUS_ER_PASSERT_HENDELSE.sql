UPDATE hendelse
SET type = 'IkkeMeldtSegPå21DagerHendelse'
WHERE type = 'MeldesyklusErPassertHendelse';

UPDATE fremtidig_hendelse
SET type = 'IkkeMeldtSegPå21DagerHendelse'
WHERE type = 'MeldesyklusErPassertHendelse';
