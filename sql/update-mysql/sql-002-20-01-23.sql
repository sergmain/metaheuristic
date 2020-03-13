CREATE TABLE mh_function_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    FUNCTION_CODE    VARCHAR(100) not null,
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_function_data_function_code_unq_idx
    ON mh_function_data (FUNCTION_CODE);

insert into mh_function_data
(ID, VERSION, FUNCTION_CODE, UPLOAD_TS, DATA, PARAMS)
select ID, VERSION, CODE, UPLOAD_TS, DATA, PARAMS
from mh_data
where DATA_TYPE=2;

commit;

alter table mh_batch add WORKBOOK_ID     NUMERIC(10, 0);

CREATE INDEX mh_batch_workbook_id_idx ON mh_batch (WORKBOOK_ID);

drop table mh_batch_workbook;

drop table mh_data;

CREATE TABLE mh_variable
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    IS_INITED       BOOLEAN not null default false,
    NAME            VARCHAR(250) not null,
    CONTEXT_ID      VARCHAR(250),
    EXEC_CONTEXT_ID     NUMERIC(10, 0),
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    FILENAME        VARCHAR(150),
    PARAMS          MEDIUMTEXT not null
);

CREATE INDEX mh_variable_exec_context_id_idx
    ON mh_variable (EXEC_CONTEXT_ID);

CREATE INDEX mh_variable_name_idx
    ON mh_variable (NAME);

CREATE TABLE mh_variable_global
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    NAME            VARCHAR(250) not null,
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    FILENAME        VARCHAR(150),
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_variable_global_name_unq_idx
    ON mh_variable_global (NAME);

truncate table mh_task;

alter table mh_experiment change WORKBOOK_ID EXEC_CONTEXT_ID decimal null;

update mh_experiment set EXEC_CONTEXT_ID=null where 1=1;

alter table mh_task
    drop column PROCESS_TYPE;





