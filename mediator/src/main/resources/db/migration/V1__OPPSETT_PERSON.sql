CREATE TABLE person
(
    id          BIGSERIAL PRIMARY KEY,
    ident       VARCHAR(11) UNIQUE,
    status      VARCHAR(50)
);

CREATE TABLE status_historikk
(
    id           BIGSERIAL PRIMARY KEY,
    person_id    BIGINT,
    dato         TIMESTAMP,
    status       VARCHAR(50),
    FOREIGN KEY (person_id) REFERENCES person (id)
);

CREATE TABLE hendelse
(
    person_id    BIGINT,
    referanse_id VARCHAR(255) UNIQUE,
    dato         TIMESTAMP,
    status       VARCHAR(50),
    kilde        VARCHAR(50),
    type         VARCHAR(50),
    FOREIGN KEY (person_id) REFERENCES person (id)
);

CREATE INDEX IF NOT EXISTS person_ident_index ON person(ident);
CREATE INDEX IF NOT EXISTS status_historikk_person_id_index ON status_historikk(person_id);
CREATE INDEX IF NOT EXISTS hendelse_person_id_index ON hendelse(person_id);
