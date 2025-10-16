ALTER TABLE person ADD COLUMN har_rett_til_dp BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE person p1
SET har_rett_til_dp = (
    SELECT
        CASE
            WHEN vedtak = 'INNVILGET' THEN TRUE
            ELSE FALSE
            END AS har_rett_til_dp
    FROM person p2
    WHERE p2.id = p1.id
);

ALTER TABLE person DROP COLUMN vedtak;
