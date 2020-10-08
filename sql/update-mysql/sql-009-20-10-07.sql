CREATE TABLE mh_cache
(
    ID                  INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             NUMERIC(5, 0)  NOT NULL,
    CREATED_ON          bigint not null,
    KEY_SHA256_LENGTH   VARCHAR(100) NOT NULL,
    KEY_VALUE           VARCHAR(512) NOT NULL,
    DATA                LONGBLOB NOT NULL
);

CREATE UNIQUE INDEX mh_cache_key_sha256_length_unq_idx
    ON mh_cache (KEY_SHA256_LENGTH);

