CREATE TABLE MH_EVENT
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    PERIOD          NUMERIC(6, 0)  not null ,
    EVENT           VARCHAR(50)    not null,
    PARAMS          TEXT     not null
);

CREATE INDEX MH_EVENT_PERIOD_IDX
    ON MH_EVENT (PERIOD);