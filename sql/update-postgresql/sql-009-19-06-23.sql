alter table mh_experiment
    add PARAMS        TEXT not null;

delete from mh_experiment_task_feature;

delete from mh_experiment_hyper_params;

delete from mh_experiment_feature;

delete from mh_experiment;

delete from mh_atlas;

drop table mh_experiment_snippet;

alter table mh_experiment
    drop column name;

alter table mh_experiment
    drop column description;

alter table mh_experiment
    drop column seed;



