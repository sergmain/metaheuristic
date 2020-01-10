CREATE UNIQUE INDEX mh_account_username_unq_idx
    ON mh_account (USERNAME);

alter table mh_company
    add UNIQUE_ID int unsigned null;

CREATE UNIQUE INDEX mh_company_unique_id_unq_idx
    ON mh_company (UNIQUE_ID);

update mh_company
set UNIQUE_ID = ID;

alter table mh_company modify UNIQUE_ID int unsigned not null;

create table mh_ids
(
    ID int unsigned NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    STUB varchar(1) null
) AUTO_INCREMENT = 2;

insert into mh_ids (stub)
select 'a' from mh_company

