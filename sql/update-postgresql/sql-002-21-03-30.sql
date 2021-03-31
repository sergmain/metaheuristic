CREATE TABLE mh_series
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    NAME            VARCHAR(255)    NOT NULL,
    PARAMS          TEXT      not null
);