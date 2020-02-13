alter table pilot_batch
    add EXEC_STATE  tinyint(1) not null default 0;

alter table pilot_batch
    add   PARAMS          MEDIUMTEXT ;

alter table pilot_batch
    add DATA_ID          NUMERIC(10, 0);

alter table mh_variable
    change EXEC_CONTEXT_ID REF_ID decimal null;

alter table mh_variable
    add   REF_TYPE    VARCHAR(15);

update mh_variable
set
    ref_type = 'execContext'
where ref_id is not null;


