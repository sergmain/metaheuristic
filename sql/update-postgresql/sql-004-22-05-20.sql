alter table MH_TASK
    rename column processor_id to core_id;

CREATE INDEX MH_PROCESSOR_CORE_PROCESSOR_ID_IDX
    ON MH_PROCESSOR_CORE (PROCESSOR_ID);

alter table MH_PROCESSOR_CORE
    add CORE_CODE varchar(20) null;
