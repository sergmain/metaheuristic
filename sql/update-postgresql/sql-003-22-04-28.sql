rename table mh_processor to mh_processor_core;

CREATE TABLE MH_PROCESSOR
(
    ID          SERIAL PRIMARY KEY,
    VERSION     NUMERIC(10, 0)  NOT NULL,
    UPDATED_ON  bigint not null,
    IP          VARCHAR(30),
    DESCRIPTION VARCHAR(250),
    STATUS      TEXT NOT NULL
);