alter table aiai_lp_flow rename to AIAI_PLAN;

alter table aiai_lp_atlas rename to aiai_atlas;

alter table aiai_lp_data rename to aiai_data;

alter table aiai_lp_experiment rename to aiai_experiment;

alter table aiai_lp_experiment_feature rename to aiai_experiment_feature;

alter table aiai_lp_experiment_hyper_params rename to aiai_experiment_hyper_params;

alter table aiai_lp_experiment_snippet rename to aiai_experiment_snippet;

alter table aiai_lp_experiment_task_feature rename to aiai_experiment_task_feature;

alter table aiai_lp_flow_instance rename to aiai_workbook;

alter table aiai_lp_launchpad_address rename to aiai_launchpad_address;

alter table aiai_lp_snippet rename to aiai_snippet;

alter table aiai_lp_station rename to aiai_station;

alter table aiai_lp_task rename to aiai_task;

alter table aiai_experiment rename column flow_instance_id to workbook_id;

alter table aiai_data rename column flow_instance_id to WORKBOOK_ID;

alter table aiai_experiment_task_feature rename column flow_instance_id to workbook_id;

alter table aiai_task rename column flow_instance_id to workbook_id;

alter table aiai_workbook rename column PLAN_ID to plan_id;

alter table pilot_batch rename column flow_id to plan_id;

alter table pilot_batch_workbook rename column flow_instance_id to workbook_id;

-- ======================

alter table aiai_atlas rename constraint aiai_lp_atlas_pkey to aiai_atlas_pkey;

alter table aiai_data rename constraint aiai_lp_data_pkey to aiai_data_pkey;

alter index aiai_lp_data_code_unq_idx rename to aiai_data_code_unq_idx;

alter index aiai_lp_data_pool_code_id_idx rename to aiai_data_pool_code_id_idx;

