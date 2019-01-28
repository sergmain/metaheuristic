alter table aiai_lp_data
  add STORAGE_URL varchar(250);

update aiai_lp_data
set STORAGE_URL = 'launchpad://';

alter table aiai_lp_data
  alter column STORAGE_URL set not null;

alter table aiai_lp_data
  alter column CODE set not null;

alter table aiai_lp_data
  alter column POOL_CODE set not null;



