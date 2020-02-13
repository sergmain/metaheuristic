CREATE TABLE MH_SNIPPET_DATA
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    SNIPPET_CODE    VARCHAR(100) not null,
    UPLOAD_TS       TIMESTAMP DEFAULT to_timestamp(0),
    DATA            OID,
    PARAMS          TEXT not null
);

CREATE UNIQUE INDEX MH_SNIPPET_DATA_SNIPPET_CODE_UNQ_IDX
    ON MH_SNIPPET_DATA (SNIPPET_CODE);

insert into MH_SNIPPET_DATA
(ID, VERSION, SNIPPET_CODE, UPLOAD_TS, DATA, PARAMS)
select ID, VERSION, CODE, UPLOAD_TS, DATA, PARAMS
from mh_data
where DATA_TYPE=2;

commit;

alter table MH_BATCH add WORKBOOK_ID       NUMERIC(10, 0)

CREATE INDEX MH_BATCH_WORKBOOK_ID_IDX ON MH_BATCH (WORKBOOK_ID);

drop table MH_BATCH_WORKBOOK;

drop table mh_data;

CREATE TABLE MH_VARIABLE
(
    ID            SERIAL PRIMARY KEY,
    VERSION       NUMERIC(5, 0) NOT NULL,
    IS_INITED     BOOLEAN default false not null,
    NAME          VARCHAR(250) not null,
    CONTEXT_ID    VARCHAR(250),
    EXEC_CONTEXT_ID   NUMERIC(10, 0),
    UPLOAD_TS     TIMESTAMP DEFAULT to_timestamp(0),
    DATA          OID,
    FILENAME      VARCHAR(150),
    PARAMS        TEXT not null
);

CREATE INDEX MH_VARIABLE_EXEC_CONTEXT_ID_IDX
    ON MH_VARIABLE (EXEC_CONTEXT_ID);

CREATE INDEX MH_DATA_VAR_ID_IDX
    ON MH_VARIABLE (NAME);

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

truncate table mh_task;

update mh_experiment set WORKBOOK_ID=null where 1=1;

update mh_plan set IS_LOCKED=0 where 1=1;

alter table mh_task
    drop column PROCESS_TYPE;

