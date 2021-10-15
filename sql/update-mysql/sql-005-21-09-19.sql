alter table mh_exec_context
    add ROOT_EXEC_CONTEXT_ID    INT UNSIGNED;

CREATE INDEX mh_exec_context_root_exec_context_id_idx
    ON mh_exec_context (ROOT_EXEC_CONTEXT_ID);



