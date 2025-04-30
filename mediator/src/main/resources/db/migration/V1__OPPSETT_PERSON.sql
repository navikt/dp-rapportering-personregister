CREATE TABLE person
(
    id          BIGSERIAL PRIMARY KEY,
    ident       VARCHAR(11) UNIQUE NOT NULL,
    status      VARCHAR(50)        NOT NULL,
    meldeplikt  BOOLEAN            NOT NULL,
    meldegruppe VARCHAR(50)
);

CREATE TABLE status_historikk
(
    id        BIGSERIAL PRIMARY KEY,
    person_id BIGINT      NOT NULL,
    dato      TIMESTAMP   NOT NULL,
    status    VARCHAR(50) NOT NULL,
    FOREIGN KEY (person_id) REFERENCES person (id)
);

CREATE TABLE hendelse
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

CREATE INDEX IF NOT EXISTS person_ident_index ON person(ident);
CREATE INDEX IF NOT EXISTS status_historikk_person_id_index ON status_historikk(person_id);
CREATE INDEX IF NOT EXISTS hendelse_person_id_index ON hendelse(person_id);
