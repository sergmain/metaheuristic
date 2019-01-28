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

drop index AIAI_LP_DATA_CODE_UNQ_IDX on AIAI_LP_DATA;

CREATE UNIQUE INDEX AIAI_LP_DATA_CODE_STORAGE_URL_UNQ_IDX
  ON AIAI_LP_DATA (CODE, STORAGE_URL);

