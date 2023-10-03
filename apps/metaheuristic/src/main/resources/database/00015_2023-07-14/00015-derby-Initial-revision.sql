-- ===============  DO NOT UPDATE THIS COPYRIGHT =========================
--  Metaheuristic, Copyright (C) 2017-20xx, Innovation platforms, LLC
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU General Public License as published by
--  the Free Software Foundation, version 3 of the License.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU General Public License for more details.
--
--  You should have received a copy of the GNU General Public License
--  along with this program.  If not, see <https://www.gnu.org/licenses/>.


create table mh_ids
(
    ID      bigint NOT NULL PRIMARY KEY,
    STUB    varchar(1)
);

CREATE TABLE mh_function_execution_time
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(10, 0)  NOT NULL,
    CREATED_ON          bigint not null,
    FUNCTION_TYPE       VARCHAR(50) NOT NULL,
    KEY_SHA256_LENGTH   VARCHAR(100) NOT NULL,
    KEY_VALUE           VARCHAR(512) NOT NULL,
    PARAMS              LONG VARCHAR 
);

CREATE UNIQUE INDEX mh_function_execution_time_key_sha256_length_unq_idx
    ON mh_function_execution_time (KEY_SHA256_LENGTH);

CREATE INDEX mh_function_execution_time_function_type_idx
    ON mh_function_execution_time (FUNCTION_TYPE);

CREATE TABLE mh_cache_process
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(10, 0)  NOT NULL,
    CREATED_ON          bigint not null,
    FUNCTION_CODE       VARCHAR(100) NOT NULL,
    KEY_SHA256_LENGTH   VARCHAR(100) NOT NULL,
    KEY_VALUE           VARCHAR(512) NOT NULL
);

CREATE UNIQUE INDEX mh_cache_process_key_sha256_length_unq_idx
    ON mh_cache_process (KEY_SHA256_LENGTH);

CREATE INDEX mh_cache_process_function_code_idx
    ON mh_cache_process (FUNCTION_CODE);

CREATE TABLE mh_cache_variable
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(10, 0)  NOT NULL,
    CACHE_PROCESS_ID    NUMERIC(10, 0) not null,
    VARIABLE_NAME       VARCHAR(250) NOT NULL,
    CREATED_ON          bigint not null,
    DATA                BLOB,
    IS_NULLIFIED        BOOLEAN not null default false
);

CREATE INDEX mh_cache_variable_cache_function_id_idx
    ON mh_cache_variable (CACHE_PROCESS_ID);

create table mh_gen_ids
(
    SEQUENCE_NAME       varchar(50) not null,
    SEQUENCE_NEXT_VALUE bigint  NOT NULL
);

CREATE UNIQUE INDEX mh_gen_ids_sequence_name_unq_idx
    ON mh_gen_ids (SEQUENCE_NAME);

CREATE TABLE mh_dispatcher
(
    ID          bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION     NUMERIC(5, 0) NOT NULL,
    CODE        VARCHAR(50)   NOT NULL,
    PARAMS      LONG VARCHAR
);

CREATE UNIQUE INDEX mh_dispatcher_code_unq_idx
    ON mh_dispatcher (CODE);

CREATE TABLE mh_company
(
    ID          bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION     NUMERIC(5, 0) NOT NULL,
    UNIQUE_ID   NUMERIC(10, 0) not null,
    NAME        VARCHAR(50)   NOT NULL,
    PARAMS      LONG VARCHAR
);

CREATE UNIQUE INDEX mh_company_unique_id_unq_idx
    ON mh_company (UNIQUE_ID);

insert into mh_company
(version, UNIQUE_ID, name, params)
VALUES
(0, 1, 'Main company', '');

insert into mh_company
(version, UNIQUE_ID, name, params)
VALUES
(0, 2, 'Company #1', '');

-- !!! this insert must be executed after creating 'master company' immediately;

insert into mh_gen_ids
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
select 'mh_ids', max(UNIQUE_ID) from mh_company;

create table mh_account
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0) not null,
    COMPANY_ID          NUMERIC(10, 0) NOT NULL,
    USERNAME            varchar(30)   NOT NULL,
    PASSWORD            varchar(100)  NOT NULL,
    ROLES               varchar(100),
    PUBLIC_NAME         varchar(100) NOT NULL,

    is_acc_not_expired  BOOLEAN not null default true,
    is_not_locked       BOOLEAN not null default false,
    is_cred_not_expired BOOLEAN not null default false,
    is_enabled          BOOLEAN not null default false,

    mail_address        varchar(100),
    PHONE               varchar(100),
    PHONE_AS_STR        varchar(100),

    CREATED_ON          bigint not null,
    UPDATED_ON          bigint not null,
    SECRET_KEY          varchar(25),
    TWO_FA              BOOLEAN not null default false,
    PARAMS              LONG VARCHAR
);

CREATE INDEX mh_account_company_id_idx
    ON mh_account (COMPANY_ID);

CREATE UNIQUE INDEX mh_account_username_unq_idx
    ON mh_account (USERNAME);

insert into mh_account
(version, COMPANY_ID, is_acc_not_expired, is_not_locked, is_cred_not_expired, is_enabled, USERNAME, PASSWORD, PUBLIC_NAME, ROLES, CREATED_ON, UPDATED_ON)
VALUES
(0, 1, true, true, true, true,
'rest_user', '$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu',
'Rest user', 'ROLE_ASSET_REST_ACCESS, ROLE_SERVER_REST_ACCESS',
1696034005027,
1696034005027);

insert into mh_account
(version, COMPANY_ID, is_acc_not_expired, is_not_locked, is_cred_not_expired, is_enabled, USERNAME, PASSWORD, PUBLIC_NAME, ROLES, CREATED_ON, UPDATED_ON)
VALUES
(0, 2, true, true, true, true, 'qqq', '$2a$10$jaQkP.gqwgenn.xKtjWIbeP4X.LDJx92FKaQ9VfrN2jgdOUTPTMIu', 'admin for company #1', 'ROLE_ADMIN', 1696034005027, 1696034005027);


CREATE TABLE mh_processor
(
    ID          bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION     NUMERIC(10, 0) NOT NULL,
    UPDATED_ON  bigint         not null,
    IP          VARCHAR(30),
    DESCRIPTION VARCHAR(250),
    STATUS      LONG VARCHAR           NOT NULL
);


CREATE TABLE mh_processor_core
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0) NOT NULL,
    PROCESSOR_ID    NUMERIC(10, 0) NOT NULL,
    UPDATED_ON      bigint         not null,
    CORE_CODE       VARCHAR(20),
    DESCRIPTION     VARCHAR(250),
    STATUS          LONG VARCHAR           NOT NULL
);

CREATE INDEX mh_processor_core_processor_id_idx
    ON mh_processor_core (PROCESSOR_ID);

CREATE TABLE mh_log_data
(
    ID        bigint generated by default as identity (start with 1) PRIMARY KEY,
    REF_ID    NUMERIC(10, 0) NOT NULL,
    VERSION   NUMERIC(5, 0)  NOT NULL,
    UPDATE_TS TIMESTAMP      not NULL default CURRENT_TIMESTAMP,
    LOG_TYPE  NUMERIC(5, 0)  NOT NULL,
    LOG_DATA  LONG VARCHAR    not null
);

CREATE TABLE mh_variable
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0)   NOT NULL,
    IS_INITED           BOOLEAN         not null default false,
    IS_NULLIFIED        BOOLEAN         not null default false,
    NAME                VARCHAR(250)    not null,
    TASK_CONTEXT_ID     VARCHAR(250)    not null,
    EXEC_CONTEXT_ID     NUMERIC(10, 0)  not null,
    VARIABLE_BLOB_ID    NUMERIC(10),
    UPLOAD_TS           TIMESTAMP       NOT NULL default CURRENT_TIMESTAMP,
    FILENAME            VARCHAR(150),
    PARAMS              VARCHAR(250)    not null
);

CREATE INDEX mh_variable_exec_context_id_idx
    ON mh_variable (EXEC_CONTEXT_ID);

CREATE INDEX mh_variable_name_idx
    ON mh_variable (NAME);

CREATE UNIQUE INDEX mh_variable_name_all_context_ids_unq_idx
    ON mh_variable (NAME, TASK_CONTEXT_ID, EXEC_CONTEXT_ID);

CREATE INDEX mh_variable_variable_blob_id_unq_idx
    ON mh_variable (VARIABLE_BLOB_ID);

CREATE TABLE mh_variable_blob
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0)   NOT NULL,
    DATA                BLOB        not null
);

-- its name is VARIABLE_GLOBAL, not GLOBAL_VARIABLE because I want these tables to be in the same spot in scheme

CREATE TABLE mh_variable_global
(
    ID          bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION     NUMERIC(5, 0)   NOT NULL,
    NAME        VARCHAR(250)    not null,
    UPLOAD_TS   TIMESTAMP       NOT NULL default CURRENT_TIMESTAMP,
    DATA        BLOB        NOT NULL,
    FILENAME    VARCHAR(150),
    PARAMS      LONG VARCHAR      not null
);

CREATE UNIQUE INDEX mh_variable_global_name_unq_idx
    ON mh_variable_global (NAME);

CREATE TABLE mh_function_data
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0)   NOT NULL,
    FUNCTION_CODE    VARCHAR(100)   not null,
    UPLOAD_TS       TIMESTAMP       NOT NULL default CURRENT_TIMESTAMP,
    DATA            BLOB,
    PARAMS          LONG VARCHAR      not null
);

CREATE UNIQUE INDEX mmh_function_data_function_code_idx
    ON mh_function_data (FUNCTION_CODE);

CREATE TABLE mh_experiment
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    EXEC_CONTEXT_ID NUMERIC(10, 0),
    CODE            VARCHAR(255)   NOT NULL,
    PARAMS          LONG VARCHAR    not null
);

CREATE UNIQUE INDEX mh_experiment_exec_context_id_unq_idx
    ON mh_experiment (EXEC_CONTEXT_ID);

CREATE UNIQUE INDEX mh_experiment_code_unq_idx
    ON mh_experiment (CODE);

CREATE TABLE mh_series
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    NAME            VARCHAR(255)    NOT NULL,
    PARAMS          LONG VARCHAR      not null
);

CREATE UNIQUE INDEX mh_series_name_unq_idx
    ON mh_series (NAME);

CREATE TABLE mh_task
(
    ID                           bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION                      NUMERIC(5, 0)  NOT NULL,
    PARAMS                       LONG VARCHAR     not null,
    CORE_ID                      NUMERIC(10, 0),
    ASSIGNED_ON                  bigint,
    UPDATED_ON                   bigint,
    COMPLETED_ON                 bigint,
    IS_COMPLETED                 smallint     not null default 0,
    FUNCTION_EXEC_RESULTS        LONG VARCHAR,
    EXEC_CONTEXT_ID              NUMERIC(10, 0) NOT NULL,
    EXEC_STATE                   smallint     not null default 0,
    IS_RESULT_RECEIVED           smallint     not null default 0,
    RESULT_RESOURCE_SCHEDULED_ON bigint,
    ACCESS_BY_PROCESSOR_ON       bigint
);

CREATE INDEX mh_task_core_id_idx
    ON mh_task (CORE_ID);

CREATE INDEX mh_task_exec_context_id_idx
    ON mh_task (EXEC_CONTEXT_ID);

CREATE TABLE mh_function
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    FUNCTION_CODE   VARCHAR(100)  not null,
    FUNCTION_TYPE   VARCHAR(50)   not null,
    PARAMS          LONG VARCHAR    not null
);

CREATE UNIQUE INDEX mh_function_function_code_unq_idx
    ON mh_function (FUNCTION_CODE);

CREATE INDEX mh_function_function_type_idx
    ON mh_function (FUNCTION_TYPE);

CREATE TABLE mh_source_code
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    COMPANY_ID      NUMERIC(10, 0) NOT NULL,
    UID             varchar(250) NOT NULL,
    CREATED_ON      bigint NOT NULL,
    PARAMS          LONG VARCHAR not null,
    IS_LOCKED       BOOLEAN not null default false,
    IS_VALID        BOOLEAN not null default false
);

CREATE UNIQUE INDEX mh_source_code_uid_unq_idx
    ON mh_source_code (UID);

CREATE TABLE mh_exec_context
(
    ID                      bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION                 NUMERIC(5, 0)  NOT NULL,
    SOURCE_CODE_ID          NUMERIC(10, 0) NOT NULL,
    COMPANY_ID              NUMERIC(10, 0) NOT NULL,
    CREATED_ON              bigint         NOT NULL,
    COMPLETED_ON            bigint,
    PARAMS                  LONG VARCHAR       NOT NULL,
    IS_VALID                BOOLEAN        default false not null,
    STATE                   smallint       not null default 0,
    CTX_GRAPH_ID            bigint NOT NULL,
    CTX_TASK_STATE_ID       bigint NOT NULL,
    CTX_VARIABLE_STATE_ID   bigint NOT NULL,
    ROOT_EXEC_CONTEXT_ID    bigint,
    ACCOUNT_ID              NUMERIC(10, 0)
);

CREATE INDEX mh_exec_context_state_idx
    ON mh_exec_context (STATE);

CREATE INDEX mh_exec_context_id_source_code_id_idx
    ON mh_exec_context (ID, SOURCE_CODE_ID);

CREATE INDEX mh_exec_context_root_exec_context_id_idx
    ON mh_exec_context (ROOT_EXEC_CONTEXT_ID);

CREATE TABLE mh_exec_context_graph
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0) NOT NULL,
    EXEC_CONTEXT_ID     bigint default NULL,
    CREATED_ON          bigint not null,
    PARAMS              LONG VARCHAR NOT NULL
);

CREATE TABLE mh_exec_context_task_state
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0) NOT NULL,
    EXEC_CONTEXT_ID     bigint default NULL,
    CREATED_ON          bigint not null,
    PARAMS              LONG VARCHAR NOT NULL
);

CREATE TABLE mh_exec_context_variable_state
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(5, 0) NOT NULL,
    EXEC_CONTEXT_ID     bigint default NULL,
    CREATED_ON          bigint not null,
    PARAMS              LONG VARCHAR NOT NULL
);

CREATE TABLE mh_experiment_result
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    COMPANY_ID      NUMERIC(10, 0) NOT NULL,
    NAME            VARCHAR(50)   NOT NULL,
    DESCRIPTION     VARCHAR(250)  NOT NULL,
    CODE            VARCHAR(50)   NOT NULL,
    CREATED_ON      bigint        not null,
    EXPERIMENT      LONG VARCHAR      NOT NULL
);

CREATE TABLE mh_experiment_task
(
    ID                      bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION                 NUMERIC(5, 0)  NOT NULL,
    EXPERIMENT_RESULT_ID    NUMERIC(10, 0) NOT NULL,
    TASK_ID                 NUMERIC(10, 0) NOT NULL,
    PARAMS                  LONG VARCHAR     not null
);

CREATE INDEX mh_experiment_task_experiment_result_id_idx
    ON mh_experiment_task (EXPERIMENT_RESULT_ID);

CREATE UNIQUE INDEX mh_experiment_task_experiment_result_id_task_id_unq_idx
    ON mh_experiment_task (EXPERIMENT_RESULT_ID, TASK_ID);

create table mh_batch
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0)       NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)      NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)      NOT NULL,
    SOURCE_CODE_ID  NUMERIC(10, 0)      NOT NULL,
    EXEC_CONTEXT_ID     NUMERIC(10, 0),
    DATA_ID         NUMERIC(10, 0),
    CREATED_ON      bigint              NOT NULL,
    EXEC_STATE      smallint             not null default 0,
    PARAMS          LONG VARCHAR,
    IS_DELETED      BOOLEAN             not null default false
);

CREATE INDEX mh_batch_exec_context_id_idx
    ON mh_batch (EXEC_CONTEXT_ID);

CREATE INDEX mh_batch_company_id_idx
    ON mh_batch (COMPANY_ID);

CREATE INDEX mh_batch_exec_state_idx
    ON mh_batch (EXEC_STATE);

create table mh_heuristic
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    COMPANY_ID          NUMERIC(10, 0) NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    PARAMS          LONG VARCHAR,
    IS_DELETED      BOOLEAN not null default false
);

create table mh_evaluation
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(5, 0)  NOT NULL,
    HEURISTIC_ID          NUMERIC(10, 0) NOT NULL,
    CREATED_ON      bigint         NOT NULL,
    PARAMS          LONG VARCHAR,
    IS_DELETED      BOOLEAN not null default false
);

CREATE TABLE mh_event
(
    ID          bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    -- company_id can be null
    COMPANY_ID          NUMERIC(10, 0),
    CREATED_ON      BIGINT NOT NULL,
    PERIOD          INT not null ,
    EVENT           VARCHAR(50)     not null,
    PARAMS          LONG VARCHAR      not null
);

CREATE INDEX mh_event_period_idx
    ON mh_event (PERIOD);


-- Specific for MHPB DDLs

CREATE table mhbp_auth
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    CODE            VARCHAR(50)     NOT NULL,
    DISABLED        BOOLEAN         not null default false,
    PARAMS          LONG VARCHAR    not null
);

CREATE INDEX mhbp_auth_company_id_idx
    ON mhbp_auth (COMPANY_ID);

CREATE table mhbp_api
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    NAME            VARCHAR(250)    NOT NULL,
    CODE            VARCHAR(50)     NOT NULL,
    DISABLED        BOOLEAN         not null default false,
    SCHEME          VARCHAR(4096)   not null
);

CREATE INDEX mhbp_api_company_id_idx
    ON mhbp_api (COMPANY_ID);

CREATE UNIQUE INDEX mhbp_api_code_idx
    ON mhbp_api (CODE);

CREATE table mhbp_kb
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    CODE            VARCHAR(50)     NOT NULL,
    DISABLED        BOOLEAN         not null default false,
    PARAMS          VARCHAR(4096)   not null,
    STATUS          smallint         NOT NULL default 0
);

CREATE INDEX mhbp_kb_company_id_idx
    ON mhbp_kb (COMPANY_ID);

CREATE table mhbp_chapter
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    KB_ID           NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    CODE            VARCHAR(100)    NOT NULL,
    DISABLED        BOOLEAN         not null default false,
    PARAMS          VARCHAR(512)   ,
    STATUS          smallint         NOT NULL default 0,
    PROMPT_COUNT    int             not null
);

CREATE UNIQUE INDEX mhbp_chapter_kb_id_code_idx
    ON mhbp_chapter (KB_ID, CODE);

CREATE table mhbp_chat
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    NAME            VARCHAR(100)    NOT NULL,
    PARAMS          LONG VARCHAR not null
);

CREATE table mhbp_chat_log
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    PARAMS          LONG VARCHAR not null
);

CREATE table mhbp_part
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    CHAPTER_ID      NUMERIC(10, 0)  NOT NULL,
    PARAMS          LONG VARCHAR not null
);

CREATE INDEX mhbp_part_chapter_id_idx
    ON mhbp_part (CHAPTER_ID);

CREATE table mhbp_answer
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    SESSION_ID      NUMERIC(10, 0)  NOT NULL,
    CHAPTER_ID      NUMERIC(10, 0)  NOT NULL,
    ANSWERED_ON     bigint          NOT NULL,
    Q_CODE          VARCHAR(50)     NOT NULL,
    STATUS          smallint         NOT NULL,
    PARAMS          LONG VARCHAR not null,
    TOTAL           int             not null,
    FAILED          int             not null,
    SYSTEM_ERROR    int             not null
);

CREATE INDEX mhbp_answer_company_id_idx
    ON mhbp_answer (SESSION_ID);

CREATE table mhbp_session
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    EVALUATION_ID   NUMERIC(10, 0)  NOT NULL,
    STARTED_ON      bigint          NOT NULL,
    PROVIDER_CODE   VARCHAR(50)     NOT NULL,
    FINISHED_ON     bigint,
    STATUS          smallint         NOT NULL
);

CREATE INDEX mhbp_session_company_id_idx
    ON mhbp_session (COMPANY_ID);

CREATE table mhbp_evaluation
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    API_ID          NUMERIC(10, 0)  NOT NULL,
    CHAPTER_IDS     VARCHAR(2048)   NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    CODE            VARCHAR(50)     NOT NULL
);

CREATE INDEX mhbp_evaluation_company_id_idx
    ON mhbp_evaluation (COMPANY_ID);

CREATE table mhbp_scenario_group
(
    ID              bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION         NUMERIC(10, 0)  NOT NULL,
    COMPANY_ID      NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID      NUMERIC(10, 0)  NOT NULL,
    CREATED_ON      bigint          NOT NULL,
    NAME            VARCHAR(250)     NOT NULL,
    DESCRIPTION     VARCHAR(1000)    NOT NULL
);

CREATE INDEX mhbp_scenario_group_account_id_idx
    ON mhbp_scenario_group (ACCOUNT_ID);

CREATE table mhbp_scenario
(
    ID                  bigint generated by default as identity (start with 1) PRIMARY KEY,
    VERSION             NUMERIC(10, 0)  NOT NULL,
    ACCOUNT_ID          NUMERIC(10, 0)  NOT NULL,
    SCENARIO_GROUP_ID   NUMERIC(10, 0)  NOT NULL,
    CREATED_ON          bigint          NOT NULL,
    NAME                VARCHAR(50)     NOT NULL,
    DESCRIPTION         VARCHAR(250)    NOT NULL,
    PARAMS              LONG VARCHAR not null
);

CREATE INDEX mhbp_scenario_account_id_scenario_group_id_idx
    ON mhbp_scenario (ACCOUNT_ID, SCENARIO_GROUP_ID);

