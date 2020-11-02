CREATE TABLE mh_cache_process
(
    ID                  INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             NUMERIC(5, 0)  NOT NULL,
    CREATED_ON          bigint not null,
    KEY_SHA256_LENGTH   VARCHAR(100) NOT NULL,
    KEY_VALUE           VARCHAR(512) NOT NULL
);

CREATE UNIQUE INDEX mh_cache_process_key_sha256_length_unq_idx
    ON mh_cache_process (KEY_SHA256_LENGTH);

CREATE TABLE mh_cache_variable
(
    ID                  INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             NUMERIC(5, 0)  NOT NULL,
    CACHE_PROCESS_ID    INT UNSIGNED    not null,
    VARIABLE_ID         INT UNSIGNED    not null,
    VARIABLE_NAME       VARCHAR(250) NOT NULL,
    CREATED_ON          bigint not null,
    DATA                LONGBLOB,
    IS_NULLIFIED        BOOLEAN not null default false
);

CREATE INDEX mh_cache_variable_cache_function_id_idx
    ON mh_cache_variable (CACHE_PROCESS_ID);
