delete from mh_atlas;

drop table mh_experiment_task_feature;

drop table mh_experiment_hyper_params;

drop table mh_experiment_feature;

drop table mh_experiment;

CREATE TABLE MH_EXPERIMENT
(
    ID          SERIAL PRIMARY KEY,
    VERSION     NUMERIC(5, 0)  NOT NULL,
    WORKBOOK_ID  NUMERIC(10, 0),
    CODE        VARCHAR(50)   NOT NULL,
    PARAMS        TEXT not null
);

CREATE UNIQUE INDEX MH_EXPERIMENT_CODE_UNQ_IDX
    ON MH_EXPERIMENT (CODE);

