CREATE TABLE person (
                        ident VARCHAR(255) PRIMARY KEY
);

CREATE TABLE hendelse (
                          id UUID PRIMARY KEY,
                          ident VARCHAR(255),
                          referanse_id VARCHAR(255),
                          dato TIMESTAMP,
                          status VARCHAR(50),
                          kilde VARCHAR(50),
                          FOREIGN KEY (ident) REFERENCES person(ident)
);