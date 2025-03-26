-- Legger til arena_id i hendelse
ALTER TABLE hendelse ADD COLUMN arena_id INTEGER;

-- Legger til arena_id i fremtidig_hendelse
ALTER TABLE fremtidig_hendelse ADD COLUMN arena_id INTEGER;
