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
    STATE   smallint not null default 0
);

drop table mh_batch;

create table mh_batch
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
    ACCOUNT_ID      INT UNSIGNED,
    SOURCE_CODE_ID         NUMERIC(10, 0) NOT NULL,
    EXEC_CONTEXT_ID     NUMERIC(10, 0),
    DATA_ID         NUMERIC(10, 0),
    CREATED_ON      bigint         NOT NULL,
    EXEC_STATE      tinyint(1) not null default 0,
    PARAMS          MEDIUMTEXT,
    IS_DELETED      BOOLEAN not null default false
);

CREATE INDEX mh_batch_exec_context_id_idx
    ON mh_batch (EXEC_CONTEXT_ID);

drop table mh_task;

CREATE TABLE mh_task
(
    ID                          INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION                     INT UNSIGNED    NOT NULL,
    PARAMS                      MEDIUMTEXT not null,
    STATION_ID                  NUMERIC(10, 0),
    ASSIGNED_ON                 bigint,
    IS_COMPLETED                tinyint(1) not null default 0,
    COMPLETED_ON                bigint,
    FUNCTION_EXEC_RESULTS       MEDIUMTEXT,
    METRICS                     MEDIUMTEXT,
    EXEC_CONTEXT_ID             NUMERIC(10, 0)   NOT NULL,
    EXEC_STATE                  tinyint(1) not null default 0,
    IS_RESULT_RECEIVED          tinyint(1) not null default 0,
    RESULT_RESOURCE_SCHEDULED_ON bigint,
    EXTENDED_RESULT             MEDIUMTEXT
);

CREATE INDEX mh_task_exec_context_id_idx
    ON mh_task (EXEC_CONTEXT_ID);


alter table mh_experiment change WORKBOOK_ID EXEC_CONTEXT_ID decimal null;

drop table mh_launchpad_address;


