CREATE TABLE arbeidssoker_beslutning
(
    id          SERIAL PRIMARY KEY,
    person_id   BIGINT      NOT NULL,
    periode_id  UUID        NOT NULL,
    handling    VARCHAR(50) NOT NULL,
    begrunnelse TEXT        NOT NULL,
    opprettet   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (person_id) REFERENCES person (id)
);


CREATE INDEX idx_arbeidssoker_beslutning_person_id ON arbeidssoker_beslutning (person_id);
