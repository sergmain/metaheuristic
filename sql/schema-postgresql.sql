create table MH_IDS
(
    ID      NUMERIC(10, 0) PRIMARY KEY,
    STUB    varchar(1) null
);

create table MH_GEN_IDS
(
    SEQUENCE_NAME       varchar(50) not null,
    SEQUENCE_NEXT_VALUE NUMERIC(10, 0)  NOT NULL
);

CREATE UNIQUE INDEX MH_GEN_IDS_SEQUENCE_NAME_UNQ_IDX
    ON MH_GEN_IDS (SEQUENCE_NAME);

CREATE TABLE MH_DISPATCHER
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    CODE            VARCHAR(50)   NOT NULL,
    PARAMS          TEXT null
);

CREATE UNIQUE INDEX MH_DISPATCHER_CODE_UNQ_IDX
    ON MH_DISPATCHER (CODE);

CREATE TABLE MH_COMPANY
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    UNIQUE_ID       NUMERIC(10, 0) NOT NULL,
    NAME            VARCHAR(50)   NOT NULL,
    PARAMS          TEXT null
);

CREATE UNIQUE INDEX MH_COMPANY_UNIQUE_ID_UNQ_IDX
    ON MH_COMPANY (UNIQUE_ID);

insert into MH_COMPANY
(id, version, UNIQUE_ID, name, params)
VALUES
(1, 0, 1, 'master company', '');

-- !!! this insert must be after creating 'master company'
insert into mh_gen_ids
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
select 'mh_ids', max(UNIQUE_ID) from mh_company;

create table MH_ACCOUNT
(
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(10, 0)  NOT NULL,
  COMPANY_ID     NUMERIC(10, 0) NOT NULL,
  USERNAME varchar(30) not null,
  PASSWORD varchar(100) not null,
  ROLES varchar(100),
  PUBLIC_NAME varchar(100),

  is_acc_not_expired BOOLEAN not null default true,
  is_not_locked BOOLEAN not null default false,
  is_cred_not_expired BOOLEAN not null default false,
  is_enabled BOOLEAN not null default false,

  mail_address varchar(100) ,
  PHONE varchar(100) ,
  PHONE_AS_STR varchar(100) ,

  CREATED_ON  bigint not null,
  UPDATED_ON  bigint not null,
  SECRET_KEY  varchar(25),
  TWO_FA      BOOLEAN not null default false
);

CREATE INDEX mh_account_company_id_idx
    ON mh_account (COMPANY_ID);

CREATE UNIQUE INDEX MH_ACCOUNT_USERNAME_UNQ_IDX
    ON MH_ACCOUNT (USERNAME);

CREATE TABLE MH_PROCESSOR
(
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(10, 0)  NOT NULL,
  UPDATED_ON  bigint not null,
  IP          VARCHAR(30),
  DESCRIPTION VARCHAR(250),
  STATUS      TEXT NOT NULL
);

CREATE TABLE MH_LOG_DATA
(
  ID          SERIAL PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT to_timestamp(0),
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    TEXT not null
);

CREATE TABLE MH_VARIABLE
(
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0) NOT NULL,
  IS_INITED     BOOLEAN not null default false,
  NAME          VARCHAR(250) not null,
  TASK_CONTEXT_ID    VARCHAR(250) not null,
  EXEC_CONTEXT_ID   NUMERIC(10, 0) not null,
  UPLOAD_TS     TIMESTAMP DEFAULT to_timestamp(0),
  DATA          OID,
  FILENAME      VARCHAR(150),
  PARAMS        TEXT not null
);

CREATE INDEX MH_VARIABLE_EXEC_CONTEXT_ID_IDX
    ON MH_VARIABLE (EXEC_CONTEXT_ID);

CREATE INDEX MH_DATA_VAR_ID_IDX
  ON MH_VARIABLE (NAME);

CREATE UNIQUE INDEX mh_variable_name_all_context_ids_unq_idx
    ON MH_VARIABLE (NAME, TASK_CONTEXT_ID, EXEC_CONTEXT_ID);

--  its name is VARIABLE_GLOBAL, not GLOBAL_VARIABLE because I want these tables to be in the same spot in scheme
CREATE TABLE MH_VARIABLE_GLOBAL
(
    ID            SERIAL PRIMARY KEY,
    VERSION       NUMERIC(5, 0) NOT NULL,
    NAME          VARCHAR(250) not null,
    UPLOAD_TS     TIMESTAMP DEFAULT to_timestamp(0),
    DATA          OID,
    FILENAME      VARCHAR(150),
    PARAMS        TEXT not null
);

CREATE UNIQUE INDEX MH_VARIABLE_GLOBAL_NAME_UNQ_IDX
    ON MH_VARIABLE_GLOBAL (NAME);

CREATE TABLE MH_FUNCTION_DATA
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    FUNCTION_CODE    VARCHAR(100) not null,
    UPLOAD_TS       TIMESTAMP DEFAULT to_timestamp(0),
    DATA            OID,
    PARAMS          TEXT not null
);

CREATE UNIQUE INDEX MH_FUNCTION_DATA_FUNCTION_CODE_UNQ_IDX
    ON MH_FUNCTION_DATA (FUNCTION_CODE);

CREATE TABLE MH_EXPERIMENT
(
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  EXEC_CONTEXT_ID  NUMERIC(10, 0),
  CODE          VARCHAR(255)   NOT NULL,
  PARAMS        TEXT not null
);

CREATE UNIQUE INDEX MH_EXPERIMENT_CODE_UNQ_IDX
  ON MH_EXPERIMENT (CODE);

CREATE TABLE MH_TASK
(
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PARAMS        TEXT not null,
  PROCESSOR_ID    NUMERIC(10, 0),
  ASSIGNED_ON   bigint,
  IS_COMPLETED  BOOLEAN default false not null,
  COMPLETED_ON  bigint,
  FUNCTION_EXEC_RESULTS  TEXT,
  EXEC_CONTEXT_ID          NUMERIC(10, 0)   NOT NULL,
  EXEC_STATE        smallint not null default 0,
  IS_RESULT_RECEIVED  BOOLEAN not null default false,
  RESULT_RESOURCE_SCHEDULED_ON  bigint
);

CREATE INDEX MH_TASK_WORKBOOK_ID_IDX
    ON MH_TASK (WORKBOOK_ID);

CREATE TABLE MH_FUNCTION
(
  ID                SERIAL PRIMARY KEY,
  VERSION           NUMERIC(5, 0)  NOT NULL,
  FUNCTION_CODE     VARCHAR(100)  not null,
  FUNCTION_TYPE     VARCHAR(50) not null,
  PARAMS            TEXT not null
);

CREATE UNIQUE INDEX MH_FUNCTION_FUNCTION_CODE_UNQ_IDX
  ON MH_FUNCTION (FUNCTION_CODE);

CREATE TABLE MH_SOURCE_CODE (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  COMPANY_ID    NUMERIC(10, 0) NOT NULL,
  UID           varchar(50)  NOT NULL,
  CREATED_ON    bigint NOT NULL,
  PARAMS        TEXT not null,
  IS_LOCKED     BOOLEAN not null default false,
  IS_VALID      BOOLEAN not null default false
);

CREATE UNIQUE INDEX MH_SOURCE_CODE_UID_UNQ_IDX
    ON MH_SOURCE_CODE (UID);

CREATE TABLE MH_EXEC_CONTEXT
(
  ID                SERIAL PRIMARY KEY,
  VERSION           NUMERIC(5, 0)  NOT NULL,
  COMPANY_ID        NUMERIC(10, 0) NOT NULL,
  SOURCE_CODE_ID    NUMERIC(10, 0) NOT NULL,
  CREATED_ON        bigint NOT NULL,
  COMPLETED_ON      bigint,
  PARAMS            TEXT NOT NULL,
  IS_VALID          BOOLEAN not null default false,
  STATE             smallint not null default 0
);

CREATE TABLE MH_EXPERIMENT_RESULT
(
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  COMPANY_ID    NUMERIC(10, 0) NOT NULL,
  NAME          VARCHAR(50)   NOT NULL,
  DESCRIPTION   VARCHAR(250)  NOT NULL,
  CODE          VARCHAR(50)   NOT NULL,
  CREATED_ON    bigint not null,
  EXPERIMENT    TEXT NOT NULL
);

CREATE TABLE MH_EXPERIMENT_TASK
(
    ID                  SERIAL PRIMARY KEY,
    VERSION             NUMERIC(5, 0)  NOT NULL,
    EXPERIMENT_RESULT_ID    NUMERIC(10, 0)   NOT NULL,
    TASK_ID             NUMERIC(10, 0)   NOT NULL,
    PARAMS              TEXT not null
);

CREATE INDEX MH_EXPERIMENT_TASK_EXPERIMENT_RESULT_ID_IDX
    ON MH_EXPERIMENT_TASK (EXPERIMENT_RESULT_ID);

CREATE UNIQUE INDEX MH_EXPERIMENT_TASK_EXPERIMENT_RESULT_ID_TASK_ID_IDX
    ON MH_EXPERIMENT_TASK (EXPERIMENT_RESULT_ID, TASK_ID);

create table MH_BATCH
(
  ID                SERIAL PRIMARY KEY,
  VERSION           NUMERIC(5, 0)  NOT NULL,
  COMPANY_ID        NUMERIC(10, 0) NOT NULL,
  ACCOUNT_ID        NUMERIC(10, 0) NOT NULL,
  SOURCE_CODE_ID           NUMERIC(10, 0) NOT NULL,
  EXEC_CONTEXT_ID       NUMERIC(10, 0),
  DATA_ID           NUMERIC(10, 0),
  CREATED_ON        bigint         NOT NULL,
  EXEC_STATE        smallint not null default 0,
  PARAMS            TEXT,
  IS_DELETED        BOOLEAN not null default false
);

CREATE INDEX MH_BATCH_EXEC_CONTEXT_ID_IDX
    ON MH_BATCH (EXEC_CONTEXT_ID);

CREATE TABLE MH_EVENT
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    -- company_id can be null
    COMPANY_ID      NUMERIC(10, 0),
    CREATED_ON      bigint         NOT NULL,
    PERIOD          NUMERIC(6, 0)  not null ,
    EVENT           VARCHAR(50)    not null,
    PARAMS          TEXT     not null
);

CREATE INDEX MH_EVENT_PERIOD_IDX
    ON MH_EVENT (PERIOD);

