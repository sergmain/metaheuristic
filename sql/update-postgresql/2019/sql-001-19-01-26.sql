alter table aiai_lp_data
  add STORAGE_URL varchar(250);

# noinspection SqlWithoutWhere
update aiai_lp_data
set STORAGE_URL = 'launchpad://';

alter table aiai_lp_data
  alter column STORAGE_URL set not null;

alter table aiai_lp_data
  alter column CODE set not null;

alter table aiai_lp_data
  alter column POOL_CODE set not null;

alter table aiai_lp_flow_instance
  rename column input_pool_code to INPUT_RESOURCE_PARAM;

alter table aiai_lp_flow_instance alter column
  input_resource_param type TEXT using input_resource_param::TEXT;

