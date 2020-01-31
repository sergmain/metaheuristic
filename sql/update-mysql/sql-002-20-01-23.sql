CREATE TABLE mh_snippet_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    SNIPPET_CODE    VARCHAR(100) not null,
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_snippet_data_snippet_code_unq_idx
    ON mh_snippet_data (SNIPPET_CODE);

insert into mh_snippet_data
(ID, VERSION, SNIPPET_CODE, UPLOAD_TS, DATA, PARAMS)
select ID, VERSION, CODE, UPLOAD_TS, DATA, PARAMS
from mh_data
where DATA_TYPE=2;

commit;

alter table mh_batch add WORKBOOK_ID     NUMERIC(10, 0);

CREATE INDEX mh_batch_workbook_id_idx ON mh_batch (WORKBOOK_ID);

drop table mh_batch_workbook;

drop table mh_data;

CREATE TABLE mh_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    VAR             VARCHAR(250) not null,
    CONTEXT_ID      VARCHAR(250),
    WORKBOOK_ID     NUMERIC(10, 0),
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    FILENAME        VARCHAR(150),
    PARAMS          MEDIUMTEXT not null
);

CREATE INDEX mh_data_workbook_id_idx
    ON mh_data (WORKBOOK_ID);

CREATE INDEX mh_data_var_id_idx
    ON mh_data (VAR);

CREATE TABLE mh_global_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    VAR             VARCHAR(250) not null,
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    FILENAME        VARCHAR(150),
    PARAMS          MEDIUMTEXT not null
);

truncate table mh_batch;

truncate table mh_task;

truncate table mh_workbook;

update mh_experiment set WORKBOOK_ID=null where 1=1;

update mh_plan set IS_LOCKED=0 where 1=1;


