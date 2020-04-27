CREATE TABLE mh_dispatcher
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    CODE            VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_dispatcher_code_unq_idx
    ON mh_dispatcher (CODE);

drop table mh_atlas;

drop table mh_atlas_task;

CREATE TABLE mh_experiment_result
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    COMPANY_ID      INT UNSIGNED    not null,
    NAME          VARCHAR(50)   NOT NULL,
    DESCRIPTION   VARCHAR(250)  NOT NULL,
    CODE          VARCHAR(50)   NOT NULL,
    CREATED_ON    bigint not null,
    EXPERIMENT    LONGTEXT NOT NULL
);

CREATE TABLE mh_experiment_task
(
    ID                      INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION                 INT UNSIGNED    NOT NULL,
    EXPERIMENT_RESULT_ID    NUMERIC(10, 0)   NOT NULL,
    TASK_ID                 NUMERIC(10, 0)   NOT NULL,
    PARAMS                  MEDIUMTEXT not null
);

CREATE INDEX mh_experiment_task_experiment_result_id_idx
    ON mh_experiment_task (EXPERIMENT_RESULT_ID);

CREATE UNIQUE INDEX mh_experiment_task_experiment_result_id_task_id_idx
    ON mh_experiment_task (EXPERIMENT_RESULT_ID, TASK_ID);

CREATE UNIQUE INDEX mh_experiment_exec_context_id_unq_idx
    ON mh_experiment (EXEC_CONTEXT_ID);