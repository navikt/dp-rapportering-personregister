-- Legger til arena_id i hendelse
ALTER TABLE hendelse ADD COLUMN arena_id VARCHAR(255);

-- Legger til arena_id i fremtidig_hendelse
ALTER TABLE fremtidig_hendelse ADD COLUMN arena_id VARCHAR(255);
