CREATE TABLE meldinger_innkommende
(
    korrelasjons_id          VARCHAR(50) NOT NULL,
    ident                    VARCHAR(11),
    relevant_meldingsinnhold JSONB
);

CREATE TABLE meldinger_utgående
(
    korrelasjons_id VARCHAR(50) NOT NULL,
    ident           VARCHAR(11) NOT NULL,
    melding         JSONB
);

CREATE INDEX IF NOT EXISTS meldinger_innkommende_korrelasjons_id_index ON meldinger_innkommende(korrelasjons_id);
CREATE INDEX IF NOT EXISTS meldinger_innkommende_ident_index ON meldinger_innkommende(ident);
CREATE INDEX IF NOT EXISTS meldinger_utgående_korrelasjons_id_index ON meldinger_utgående(korrelasjons_id);
CREATE INDEX IF NOT EXISTS meldinger_utgående_ident_index ON meldinger_utgående(ident);
