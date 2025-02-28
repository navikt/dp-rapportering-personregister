CREATE TABLE fremtidig_hendelse
(
    person_id    BIGINT              NOT NULL,
    referanse_id VARCHAR(255) UNIQUE NOT NULL,
    dato         TIMESTAMP           NOT NULL,
    start_dato   TIMESTAMP,
    slutt_dato   TIMESTAMP,
    kilde        VARCHAR(50)         NOT NULL,
    type         VARCHAR(50)         NOT NULL,
    extra        JSONB,
    FOREIGN KEY (person_id) REFERENCES person (id)
);
CREATE INDEX IF NOT EXISTS fremtidig_hendelse_start_dato_index ON fremtidig_hendelse(start_dato);
CREATE INDEX IF NOT EXISTS fremtidig_hendelse_referanse_id_index on fremtidig_hendelse(referanse_id);
