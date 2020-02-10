rename table mh_source_code to mh_source_code;

alter table mh_source_code
    drop key mh_plan_code_unq_idx;

alter table mh_source_code change CODE UID varchar(50) not null;

CREATE UNIQUE INDEX mh_source_code_uid_unq_idx
    ON mh_source_code (UID);


