alter table aiai_data rename column storage_url to PARAMS;

delete from aiai_data where PARAMS not like 'launchpad%';

-- noinspection SqlWithoutWhere
update aiai_data
set PARAMS = 'sourcing: launchpad'


