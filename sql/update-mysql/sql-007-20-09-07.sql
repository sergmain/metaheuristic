drop table mh_function;

CREATE TABLE mh_function
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    FUNCTION_CODE   VARCHAR(100)  not null,
    FUNCTION_TYPE   VARCHAR(50) not null,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_function_function_code_unq_idx
    ON mh_function (FUNCTION_CODE);