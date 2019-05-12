rename table aiai_lp_flow to AIAI_PLAN;

rename table aiai_lp_atlas to aiai_atlas;

rename table aiai_lp_data to aiai_data;

rename table aiai_lp_experiment to aiai_experiment;

rename table aiai_lp_experiment_feature to aiai_experiment_feature;

rename table aiai_lp_experiment_hyper_params to aiai_experiment_hyper_params;

rename table aiai_lp_experiment_snippet to aiai_experiment_snippet;

rename table aiai_lp_experiment_task_feature to aiai_experiment_task_feature;

rename table aiai_lp_flow_instance to aiai_workbook;

rename table aiai_lp_launchpad_address to aiai_launchpad_address;

rename table aiai_lp_snippet to aiai_snippet;

rename table aiai_lp_station to aiai_station;

rename table aiai_lp_task to aiai_task;

alter table aiai_experiment change FLOW_INSTANCE_ID WORKBOOK_ID decimal null;

alter table aiai_data change FLOW_INSTANCE_ID WORKBOOK_ID decimal null;

alter table aiai_experiment_task_feature change FLOW_INSTANCE_ID WORKBOOK_ID decimal not null;

alter table aiai_task change FLOW_INSTANCE_ID WORKBOOK_ID decimal not null;

alter table aiai_workbook change FLOW_ID PLAN_ID decimal not null;

alter table pilot_batch change FLOW_ID PLAN_ID decimal not null;

rename table pilot_batch_flow_instance to pilot_batch_workbook;

alter table pilot_batch_workbook change FLOW_INSTANCE_ID WORKBOOK_ID decimal not null;


