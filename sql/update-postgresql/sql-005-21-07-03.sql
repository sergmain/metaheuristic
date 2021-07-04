truncate table mh_cache_variable;

truncate table mh_cache_process;

alter table mh_cache_process
    add FUNCTION_CODE       VARCHAR(100) NOT NULL;

CREATE INDEX MH_CACHE_PROCESS_FUNCTION_CODE_IDX
    ON MH_CACHE_PROCESS (FUNCTION_CODE);

