alter table mh_task
    change PROCESSOR_ID CORE_ID decimal null;

CREATE INDEX mh_processor_core_processor_id_idx
    ON mh_processor_core (PROCESSOR_ID);

alter table mh_processor_core
    add CORE_CODE varchar(20) null;