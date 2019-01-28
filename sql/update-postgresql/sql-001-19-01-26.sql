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

drop index AIAI_LP_DATA_CODE_UNQ_IDX;

CREATE UNIQUE INDEX AIAI_LP_DATA_CODE_STORAGE_URL_UNQ_IDX
  ON AIAI_LP_DATA (CODE, STORAGE_URL);