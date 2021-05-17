create table mh_heuristic
(
    ID                      INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION                 INT UNSIGNED    NOT NULL,
    COMPANY_ID              INT UNSIGNED    NOT NULL,
    CREATED_ON              bigint NOT NULL,
    PARAMS                  LONGTEXT NOT NULL,
    IS_DELETED              BOOLEAN not null default false
);

create table mh_evaluation
(
    ID                      INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION                 INT UNSIGNED    NOT NULL,
    HEURISTIC_ID            INT UNSIGNED    NOT NULL,
    CREATED_ON              bigint NOT NULL,
    PARAMS                  LONGTEXT NOT NULL,
    IS_DELETED              BOOLEAN not null default false
);