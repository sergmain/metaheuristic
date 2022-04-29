rename table mh_processor to mh_processor_core;

alter table mh_processor_core
    add     PROCESSOR_ID    INT UNSIGNED;

CREATE TABLE mh_processor
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    UPDATED_ON      bigint not null,
    IP              VARCHAR(30),
    DESCRIPTION     VARCHAR(250),
    STATUS          LONGTEXT NOT NULL
);

