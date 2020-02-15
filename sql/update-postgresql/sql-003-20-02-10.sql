alter table MH_PLAN rename to MH_SOURCE_CODE;

drop index mh_plan_code_unq_idx;

alter table mh_plan rename column code to UID;

CREATE UNIQUE INDEX mh_source_code_uid_unq_idx
    ON mh_source_code (UID);

drop table MH_WORKBOOK;

CREATE TABLE MH_EXEC_CONTEXT
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    SOURCE_CODE_ID    NUMERIC(10, 0) NOT NULL,
    CREATED_ON        bigint NOT NULL,
    COMPLETED_ON      bigint,
    INPUT_RESOURCE_PARAM  TEXT NOT NULL,
    IS_VALID          BOOLEAN not null default false,
    STATE        smallint not null default 0
);

drop table MH_BATCH;

create table MH_BATCH
(
    ID                SERIAL PRIMARY KEY,
    VERSION           NUMERIC(5, 0)  NOT NULL,
    COMPANY_ID        NUMERIC(10, 0) NOT NULL,
    ACCOUNT_ID        NUMERIC(10, 0),
    SOURCE_CODE_ID    NUMERIC(10, 0) NOT NULL,
    EXEC_CONTEXT_ID   NUMERIC(10, 0),
    DATA_ID           NUMERIC(10, 0),
    CREATED_ON        bigint         NOT NULL,
    EXEC_STATE        smallint not null default 0,
    PARAMS            TEXT,
    IS_DELETED        BOOLEAN not null default false
);

CREATE INDEX MH_BATCH_EXEC_CONTEXT_ID_IDX
    ON MH_BATCH (EXEC_CONTEXT_ID);

alter table mh_task
    drop column task_order;

alter table mh_experiment rename column workbook_id to EXEC_CONTEXT_ID;

alter table mh_task rename column workbook_id to EXEC_CONTEXT_ID;


