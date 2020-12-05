CREATE INDEX mh_task_processor_id_idx
    ON mh_task (PROCESSOR_ID);

alter table mh_batch alter column exec_context_id set not null;

delete from mh_experiment where EXEC_CONTEXT_ID is null;

alter table mh_experiment alter column EXEC_CONTEXT_ID set not null;



