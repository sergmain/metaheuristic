alter table aiai_lp_flow_instance
  rename column input_pool_code to INPUT_RESOURCE_PARAM;

alter table aiai_lp_flow_instance alter column
  input_resource_param type TEXT using input_resource_param::TEXT;

