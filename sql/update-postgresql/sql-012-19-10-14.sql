CREATE TABLE MH_EVENT
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    EVENT           VARCHAR(50)    not null,
    PARAMS          TEXT     not null
);
