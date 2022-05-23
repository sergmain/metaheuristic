truncate table mh_processor;

CREATE TABLE MH_PROCESSOR_CORE
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(10, 0)  NOT NULL,
    PROCESSOR_ID      NUMERIC(10, 0) NOT NULL,
    UPDATED_ON        bigint not null,
    DESCRIPTION       VARCHAR(250),
    STATUS            TEXT NOT NULL
);
