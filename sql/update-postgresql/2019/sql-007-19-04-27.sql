truncate table aiai_lp_station;

alter table aiai_lp_station
    drop column UPDATE_TS;

alter table aiai_lp_station
    drop column ENV;

alter table aiai_lp_station
    drop column ACTIVE_TIME;

alter table AIAI_LP_STATION
    add STATUS    TEXT not null;

alter table AIAI_LP_STATION
    add   UPDATED_ON  bigint not null;
