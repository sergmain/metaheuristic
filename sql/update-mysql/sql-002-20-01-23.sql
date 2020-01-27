delete from mh_data where DATA_TYPE in (3, 4);

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

delete from mh_data where DATA_TYPE =2;

commit;

alter table mh_data drop column CHECKSUM;

alter table mh_data drop column IS_MANUAL;

alter table mh_data drop column IS_VALID;

drop index MH_DATA_REF_ID_REF_TYPE_IDX on mh_data;

alter table mh_data change REF_ID WORKBOOK_ID decimal null;

CREATE INDEX mh_data_workbook_id_idx ON mh_data (WORKBOOK_ID);

alter table mh_data drop column REF_TYPE;

alter table mh_batch add WORKBOOK_ID     NUMERIC(10, 0);

CREATE INDEX mh_batch_workbook_id_idx ON mh_batch (WORKBOOK_ID);

drop table mh_batch_workbook;

alter table mh_data drop column CODE;

alter table mh_data change POOL_CODE VAR VARCHAR(250) not null;
