CREATE TABLE arbeidssoker
(
    id                      BIGSERIAL PRIMARY KEY,
    periode_id              UUID NOT NULL UNIQUE,
    person_id               BIGINT NOT NULL,
    startet                 TIMESTAMP NOT NULL,
    avsluttet               TIMESTAMP,
    overtatt_bekreftelse    BOOLEAN,
    sist_endret             TIMESTAMP NOT NULL,
    FOREIGN KEY (person_id) REFERENCES person (id)
);

CREATE INDEX IF NOT EXISTS arbeidssoker_person_id_index ON arbeidssoker(person_id);
