CREATE TABLE temp_person
(
    ident       VARCHAR(11) UNIQUE NOT NULL ,
    status      VARCHAR (50),
    oppdatert TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
)
