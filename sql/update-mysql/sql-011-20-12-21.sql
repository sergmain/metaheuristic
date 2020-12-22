CREATE INDEX mh_batch_company_id_idx
    ON mh_batch (COMPANY_ID);

CREATE INDEX mh_source_company_id_idx
    ON mh_source_code (COMPANY_ID);

CREATE INDEX mh_exec_context_id_source_code_id_idx
    ON mh_exec_context (ID, SOURCE_CODE_ID);