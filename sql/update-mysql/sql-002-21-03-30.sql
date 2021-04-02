CREATE TABLE mh_series
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    NAME            VARCHAR(255)    NOT NULL,
    PARAMS          MEDIUMTEXT      not null
);

CREATE UNIQUE INDEX mh_series_name_unq_idx
    ON mh_series (NAME);
