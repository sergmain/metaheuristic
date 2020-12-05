drop table if exists mh_cache_process;

drop table if exists mh_cache_variable;

CREATE TABLE MH_CACHE_PROCESS
(
    ID                  SERIAL PRIMARY KEY,
    VERSION             NUMERIC(10, 0)  NOT NULL,
    CREATED_ON          bigint not null,
    KEY_SHA256_LENGTH   VARCHAR(100) NOT NULL,
    KEY_VALUE           VARCHAR(512) NOT NULL
);

CREATE UNIQUE INDEX MH_CACHE_PROCESS_KEY_SHA256_LENGTH_UNQ_IDX
    ON MH_CACHE_PROCESS (KEY_SHA256_LENGTH);

CREATE TABLE mh_cache_variable
(
    ID                  SERIAL PRIMARY KEY,
    VERSION             NUMERIC(5, 0)  NOT NULL,
    CACHE_PROCESS_ID    NUMERIC(10, 0) NOT NULL,
    VARIABLE_NAME       VARCHAR(250) NOT NULL,
    CREATED_ON          bigint not null,
    DATA                OID,
    IS_NULLIFIED        BOOLEAN not null default false
);

CREATE INDEX MH_CACHE_VARIABLE_CACHE_FUNCTION_ID_IDX
    ON MH_CACHE_VARIABLE (CACHE_PROCESS_ID);
