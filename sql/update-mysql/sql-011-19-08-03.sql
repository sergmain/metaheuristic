CREATE TABLE mh_atlas_task
(
    ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    ATLAS_ID    NUMERIC(10, 0)   NOT NULL,
    TASK_ID     NUMERIC(10, 0)   NOT NULL,
    PARAMS      MEDIUMTEXT not null
);

CREATE INDEX mh_atlas_task_atlas_id_idx
    ON mh_atlas_task (ATLAS_ID);

CREATE INDEX mh_atlas_task_atlas_id_task_id_idx
    ON mh_atlas_task (ATLAS_ID, TASK_ID);
