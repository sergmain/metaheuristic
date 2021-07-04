truncate table mh_cache_variable;

truncate table mh_cache_process;

alter table mh_cache_process
    add FUNCTION_CODE       VARCHAR(100) NOT NULL;

CREATE INDEX mh_cache_process_function_code_idx
    ON mh_cache_process (FUNCTION_CODE);



