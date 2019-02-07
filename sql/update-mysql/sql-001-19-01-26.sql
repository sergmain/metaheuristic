alter table aiai_lp_data
  add STORAGE_URL varchar(250);

# noinspection SqlWithoutWhere
update aiai_lp_data
set STORAGE_URL = 'launchpad://';

alter table aiai_lp_data
  modify STORAGE_URL varchar(250) not null;

alter table aiai_lp_data
  modify CODE VARCHAR(200) not null;

alter table aiai_lp_data
  modify POOL_CODE VARCHAR(200) not null;

alter table aiai_lp_flow_instance
  change INPUT_POOL_CODE INPUT_RESOURCE_PARAM TEXT not null;


