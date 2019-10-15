CREATE TABLE mh_event
(
    ID              INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    EVENT           VARCHAR(50)    not null,
    PARAMS          MEDIUMTEXT     not null
);
