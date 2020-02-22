alter table aiai_data drop key AIAI_LP_DATA_CODE_UNQ_IDX;

alter table aiai_data
    add constraint AIAI_DATA_CODE_UNQ_IDX
        unique (CODE);

drop index AIAI_LP_DATA_POOL_CODE_ID_IDX on aiai_data;

create index AIAI_DATA_POOL_CODE_ID_IDX
    on aiai_data (POOL_CODE);

drop index AIAI_LP_EXPERIMENT_SNIPPET_EXPERIMENT_ID_IDX on aiai_experiment_snippet;

create index AIAI_EXPERIMENT_SNIPPET_EXPERIMENT_ID_IDX
    on aiai_experiment_snippet (EXPERIMENT_ID);


alter table aiai_experiment drop key AIAI_LP_EXPERIMENT_CODE_UNQ_IDX;

alter table aiai_experiment
    add constraint AIAI_EXPERIMENT_CODE_UNQ_IDX
        unique (CODE);

alter table aiai_experiment_feature drop key AIAI_LP_EXPERIMENT_FEATURE_UNQ_IDX;

alter table aiai_experiment_feature
    add constraint AIAI_EXPERIMENT_FEATURE_UNQ_IDX
        unique (EXPERIMENT_ID, CHECKSUM_ID_CODES);

drop index AIAI_LP_EXPERIMENT_TASK_FEATURE_FLOW_INSTANCE_ID_IDX on aiai_experiment_task_feature;

create index AIAI_EXPERIMENT_TASK_FEATURE_FLOW_INSTANCE_ID_IDX
    on aiai_experiment_task_feature (WORKBOOK_ID);


alter table aiai_plan drop key AIAI_LP_FLOW_CODE_UNQ_IDX;

alter table aiai_plan
    add constraint AIAI_PLAN_CODE_UNQ_IDX
        unique (CODE);

# =============================

alter table aiai_data change STORAGE_URL PARAMS MEDIUMTEXT not null;

delete from aiai_data where PARAMS not like 'launchpad%';

# noinspection SqlWithoutWhere
update aiai_data
set PARAMS = 'sourcing: dispatcher'






