CREATE TABLE meldinger_innkommende
(
    korrelasjonsId           VARCHAR(50) NOT NULL,
    ident                    VARCHAR(11),
    relevant_meldingsinnhold JSONB
);

CREATE TABLE meldinger_utgående
(
    korrelasjonsId VARCHAR(50) NOT NULL,
    ident          VARCHAR(11) NOT NULL,
    melding        JSONB
);

CREATE INDEX IF NOT EXISTS meldinger_innkommende_korrelasjonsId_index ON meldinger_innkommende(korrelasjonsId);
CREATE INDEX IF NOT EXISTS meldinger_innkommende_ident_index ON meldinger_innkommende(ident);
CREATE INDEX IF NOT EXISTS meldinger_utgående_korrelasjonsId_index ON meldinger_utgående(korrelasjonsId);
CREATE INDEX IF NOT EXISTS meldinger_utgående_ident_index ON meldinger_utgående(ident);


ALTER TABLE hendelse ADD COLUMN korrelasjonsid VARCHAR(50);
ALTER TABLE fremtidig_hendelse ADD COLUMN korrelasjonsid VARCHAR(50);
