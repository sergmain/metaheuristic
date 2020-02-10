alter table MH_PLAN rename to MH_SOURCE_CODE;

drop index mh_plan_code_unq_idx;

alter table mh_plan rename column code to UID;

CREATE UNIQUE INDEX mh_source_code_uid_unq_idx
    ON mh_source_code (UID);

