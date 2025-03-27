-- Legger til arena_id i hendelse
ALTER TABLE hendelse ADD COLUMN arena_id INTEGER;
CREATE INDEX IF NOT EXISTS hendelse_arena_id_index ON hendelse(arena_id);

-- Legger til arena_id i fremtidig_hendelse
ALTER TABLE fremtidig_hendelse ADD COLUMN arena_id INTEGER;
CREATE INDEX IF NOT EXISTS fremtidig_hendelse_arena_id_index ON fremtidig_hendelse(arena_id);
