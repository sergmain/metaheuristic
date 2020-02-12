rename table mh_source_code to mh_source_code;

alter table mh_source_code
    drop key mh_plan_code_unq_idx;

alter table mh_source_code change CODE UID varchar(50) not null;

CREATE UNIQUE INDEX mh_source_code_uid_unq_idx
    ON mh_source_code (UID);

drop table mh_exec_context;

CREATE TABLE mh_exec_context
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    SOURCE_CODE_ID  NUMERIC(10, 0) NOT NULL,
    CREATED_ON      bigint NOT NULL,
    COMPLETED_ON    bigint,
    INPUT_RESOURCE_PARAM  LONGTEXT NOT NULL,
    IS_VALID      BOOLEAN not null default false,
    EXEC_STATE   smallint not null default 0
);
