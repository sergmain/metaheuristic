alter table pilot_batch
    add EXEC_STATE  tinyint(1) not null default 0;

alter table pilot_batch
    add   PARAMS          MEDIUMTEXT ;

alter table pilot_batch
    add DATA_ID          NUMERIC(10, 0);

alter table mh_data
    change WORKBOOK_ID REF_ID decimal null;

alter table mh_data
    add   REF_TYPE    VARCHAR(15);

update mh_data
set
    ref_type = 'workbook'
where ref_id is not null;


