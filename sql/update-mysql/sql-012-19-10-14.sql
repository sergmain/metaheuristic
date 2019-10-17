CREATE TABLE mh_event
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    CREATED_ON      BIGINT UNSIGNED NOT NULL,
    PERIOD          INT UNSIGNED    not null ,
    EVENT           VARCHAR(50)     not null,
    PARAMS          MEDIUMTEXT      not null
);

CREATE INDEX mh_event_period_idx
    ON mh_event (PERIOD);
