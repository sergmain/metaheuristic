-- names of tables must be in lower case!

CREATE TABLE mh_company
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    NAME            VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT null
);

insert into mh_company
(id, version, name, params)
VALUES
(1, 0, 'master company', '');

create table mh_account
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
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
    SECRET_KEY  varchar(25),
    TWO_FA      BOOLEAN not null default false
);

CREATE INDEX mh_account_company_id_idx
    ON mh_account (COMPANY_ID);

CREATE TABLE mh_station
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    UPDATED_ON  bigint not null,
    IP          VARCHAR(30),
    DESCRIPTION VARCHAR(250),
    STATUS      TEXT not null
);

CREATE TABLE mh_log_data
(
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    MEDIUMTEXT not null
);

CREATE TABLE mh_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    CODE        VARCHAR(200) not null,
    POOL_CODE   VARCHAR(250) not null,
    DATA_TYPE   NUMERIC(2, 0) NOT NULL,
    REF_ID      NUMERIC(10, 0),
    REF_TYPE    VARCHAR(15),
    UPLOAD_TS   TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA        LONGBLOB,
    CHECKSUM    VARCHAR(2048),
    IS_VALID    tinyint(1) not null default 0,
    IS_MANUAL   tinyint(1) not null default 0,
    FILENAME    VARCHAR(150),
    PARAMS      MEDIUMTEXT not null
);

CREATE INDEX mh_data_data_type_idx
  ON mh_data (DATA_TYPE);

CREATE INDEX mh_data_ref_id_ref_type_idx
  ON mh_data (REF_ID, REF_TYPE);

CREATE INDEX mh_data_ref_type_idx
  ON mh_data (REF_TYPE);

CREATE INDEX mh_data_pool_code_id_idx
    ON mh_data (POOL_CODE);

CREATE UNIQUE INDEX mh_data_code_unq_idx
  ON mh_data (CODE);

CREATE TABLE mh_experiment
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    WORKBOOK_ID  NUMERIC(10, 0),
    CODE        VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_experiment_code_unq_idx
  ON mh_experiment (CODE);

CREATE TABLE mh_task
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    PARAMS          MEDIUMTEXT not null,
    STATION_ID          NUMERIC(10, 0),
    ASSIGNED_ON    bigint,
    IS_COMPLETED   tinyint(1) not null default 0,
    COMPLETED_ON   bigint,
    SNIPPET_EXEC_RESULTS  MEDIUMTEXT,
    METRICS      MEDIUMTEXT,
    TASK_ORDER   smallint not null,
    WORKBOOK_ID          NUMERIC(10, 0)   NOT NULL,
    EXEC_STATE        tinyint(1) not null default 0,
    IS_RESULT_RECEIVED  tinyint(1) not null default 0,
    RESULT_RESOURCE_SCHEDULED_ON bigint,
    PROCESS_TYPE tinyint(1) not null,
    EXTENDED_RESULT      MEDIUMTEXT
);

CREATE INDEX mh_task_workbook_id_idx
    ON mh_task (WORKBOOK_ID);

CREATE INDEX mh_task_workbook_id_task_order_idx
    ON mh_task (WORKBOOK_ID, TASK_ORDER);

CREATE TABLE mh_snippet
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    SNIPPET_CODE    VARCHAR(100)  not null,
    SNIPPET_TYPE      VARCHAR(50) not null,
    PARAMS        MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_snippet_unq_idx
  ON mh_snippet (SNIPPET_CODE);

CREATE TABLE mh_plan
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
    CODE            varchar(50)  NOT NULL,
    CREATED_ON    bigint NOT NULL,
    PARAMS        TEXT not null,
    IS_LOCKED      BOOLEAN not null default false,
    IS_VALID      BOOLEAN not null default false
);

CREATE UNIQUE INDEX mh_plan_code_unq_idx
    ON mh_plan (CODE);

CREATE TABLE mh_workbook
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    PLAN_ID       NUMERIC(10, 0) NOT NULL,
    CREATED_ON    bigint NOT NULL,
    COMPLETED_ON  bigint,
    INPUT_RESOURCE_PARAM  LONGTEXT NOT NULL,
    PRODUCING_ORDER integer NOT NULL,
    IS_VALID      BOOLEAN not null default false,
    EXEC_STATE   smallint not null default 0
);

CREATE TABLE mh_atlas
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
    NAME          VARCHAR(50)   NOT NULL,
    DESCRIPTION   VARCHAR(250)  NOT NULL,
    CODE          VARCHAR(50)   NOT NULL,
    CREATED_ON    bigint not null,
    EXPERIMENT    LONGTEXT NOT NULL
);

CREATE TABLE mh_atlas_task
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    ATLAS_ID    NUMERIC(10, 0)   NOT NULL,
    TASK_ID     NUMERIC(10, 0)   NOT NULL,
    PARAMS      MEDIUMTEXT not null
);

CREATE INDEX mh_atlas_task_atlas_id_idx
    ON mh_atlas_task (ATLAS_ID);

CREATE INDEX mh_atlas_task_atlas_id_task_id_idx
    ON mh_atlas_task (ATLAS_ID, TASK_ID);

create table mh_batch
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
    ACCOUNT_ID      INT UNSIGNED,
    PLAN_ID         NUMERIC(10, 0) NOT NULL,
    DATA_ID         NUMERIC(10, 0),
    CREATED_ON      bigint         NOT NULL,
    EXEC_STATE      tinyint(1) not null default 0,
    PARAMS          MEDIUMTEXT,
    IS_DELETED      BOOLEAN not null default false
);

CREATE TABLE mh_batch_workbook
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    BATCH_ID    NUMERIC(10, 0) NOT NULL,
    WORKBOOK_ID NUMERIC(10, 0) NOT NULL
);

CREATE INDEX mh_batch_workbook_batch_id_idx
    ON mh_batch_workbook (BATCH_ID);

CREATE TABLE mh_event
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    -- company_id can be null
    COMPANY_ID      INT UNSIGNED,
    CREATED_ON      BIGINT UNSIGNED NOT NULL,
    PERIOD          INT UNSIGNED    not null ,
    EVENT           VARCHAR(50)     not null,
    PARAMS          MEDIUMTEXT      not null
);

CREATE INDEX mh_event_period_idx
    ON mh_event (PERIOD);

-- ========= legacy table

create table mh_launchpad_address
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    URL varchar(200) not null,
    DESCRIPTION varchar(100) not null,
    SIGNATURE varchar(1000)
);
