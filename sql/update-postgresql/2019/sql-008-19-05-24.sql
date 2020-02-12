alter table pilot_batch
    add EXEC_STATE  smallint not null default 0;

alter table pilot_batch
    add PARAMS        TEXT;

alter table pilot_batch
    add DATA_ID          NUMERIC(10, 0);

alter table mh_data
    rename column workbook_id to REF_ID;

alter table mh_data
    add REF_TYPE    VARCHAR(15);

update mh_data
set
    ref_type = 'execContext'
where ref_id is not null;