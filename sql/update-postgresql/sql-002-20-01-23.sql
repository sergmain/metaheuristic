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
from MH_DATA
where DATA_TYPE=2;

commit;

alter table mh_data add WORKBOOK_ID       NUMERIC(10, 0);

CREATE INDEX MH_BATCH_WORKBOOK_ID_IDX
    ON MH_BATCH (WORKBOOK_ID);

drop table MH_BATCH_WORKBOOK;

CREATE TABLE MH_DATA
(
    ID            SERIAL PRIMARY KEY,
    VERSION       NUMERIC(5, 0) NOT NULL,
    VAR           VARCHAR(250) not null,
    CONTEXT_ID    VARCHAR(250),
    WORKBOOK_ID   NUMERIC(10, 0),
    UPLOAD_TS     TIMESTAMP DEFAULT to_timestamp(0),
    DATA          OID,
    FILENAME      VARCHAR(150),
    PARAMS        TEXT not null
);

CREATE INDEX MH_DATA_WORKBOOK_ID_IDX
    ON MH_DATA (WORKBOOK_ID);

CREATE INDEX MH_DATA_VAR_ID_IDX
    ON MH_DATA (VAR);

CREATE TABLE MH_GLOBAL_DATA
(
    ID            SERIAL PRIMARY KEY,
    VERSION       NUMERIC(5, 0) NOT NULL,
    VAR           VARCHAR(250) not null,
    UPLOAD_TS     TIMESTAMP DEFAULT to_timestamp(0),
    DATA          OID,
    FILENAME      VARCHAR(150),
    PARAMS        TEXT not null
);

truncate table mh_batch;

truncate table mh_task;

truncate table mh_workbook;

update mh_experiment set WORKBOOK_ID=null where 1=1;

update mh_plan set IS_LOCKED=0 where 1=1;

alter table mh_task
    drop column PROCESS_TYPE;

