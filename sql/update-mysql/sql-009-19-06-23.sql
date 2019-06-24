delete from mh_atlas;

drop table mh_experiment_snippet;

drop table mh_experiment_task_feature;

drop table mh_experiment_hyper_params;

drop table mh_experiment_feature;

drop table mh_experiment;

CREATE TABLE mh_experiment
(
    ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    WORKBOOK_ID  NUMERIC(10, 0),
    CODE        VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_experiment_code_unq_idx
    ON mh_experiment (CODE);