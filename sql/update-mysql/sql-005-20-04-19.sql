CREATE TABLE mh_dispatcher
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    CODE            VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_dispatcher_code_unq_idx
    ON mh_dispatcher (CODE);
