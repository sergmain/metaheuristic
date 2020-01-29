delete from mh_data where DATA_TYPE in (3, 4);

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

delete from MH_DATA where DATA_TYPE =2;

commit;

alter table mh_data drop column checksum;

alter table mh_data drop column IS_MANUAL;

alter table mh_data drop column IS_VALID;

drop index mh_data_ref_id_ref_type_idx;

alter table mh_data rename column ref_id to WORKBOOK_ID;

CREATE INDEX MH_DATA_WORKBOOK_ID_IDX ON MH_DATA (WORKBOOK_ID);

alter table mh_data drop column REF_TYPE;

alter table mh_data add WORKBOOK_ID       NUMERIC(10, 0);

CREATE INDEX MH_BATCH_WORKBOOK_ID_IDX
    ON MH_BATCH (WORKBOOK_ID);

drop table MH_BATCH_WORKBOOK;

alter table mh_data drop column CODE;

alter table mh_data rename column pool_code to VAR;

alter table mh_account
    add         CONTEXT_ID      NUMERIC(10, 0);

update mh_data
set CONTEXT_ID = (select SEQUENCE_NEXT_VALUE from mh_gen_ids where SEQUENCE_NAME='mh_ids') + id;

update mh_gen_ids
set SEQUENCE_NEXT_VALUE = (
    select max(CONTEXT_ID) + 1 from mh_data
)
where SEQUENCE_NAME='mh_ids';