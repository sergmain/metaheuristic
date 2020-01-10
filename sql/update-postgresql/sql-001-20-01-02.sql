CREATE UNIQUE INDEX MH_ACCOUNT_USERNAME_UNQ_IDX
    ON MH_ACCOUNT (USERNAME);

alter table mh_company
    add     UNIQUE_ID     NUMERIC(10, 0);

update mh_company
set UNIQUE_ID = ID;

CREATE UNIQUE INDEX mh_company_unique_id_unq_idx
    ON mh_company (UNIQUE_ID);

alter table mh_account alter column UNIQUE_ID set not null;

create table mh_ids
(
    ID      SERIAL PRIMARY KEY,
    STUB    varchar(1) null
);

insert into mh_ids (stub)
select 'a' from mh_company