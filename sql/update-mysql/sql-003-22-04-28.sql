truncate table mh_processor;

CREATE TABLE mh_processor_core
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    PROCESSOR_ID    INT UNSIGNED,
    UPDATED_ON      bigint not null,
    IP              VARCHAR(30),
    DESCRIPTION     VARCHAR(250),
    STATUS          LONGTEXT NOT NULL
);

