create table mh_heuristic
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID        NUMERIC(10, 0) NOT NULL,
    CREATED_ON        bigint         NOT NULL,
    PARAMS            TEXT,
    IS_DELETED        BOOLEAN not null default false
);

create table mh_evaluation
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(10, 0)  NOT NULL,
    HEURISTIC_ID      NUMERIC(10, 0) NOT NULL,
    CREATED_ON        bigint         NOT NULL,
    PARAMS            TEXT,
    IS_DELETED        BOOLEAN not null default false
);