CREATE TABLE mh_company
(
    ID          SERIAL PRIMARY KEY,
    VERSION     NUMERIC(10, 0)  NOT NULL,
    NAME            VARCHAR(50)   NOT NULL,
    PARAMS          TEXT null
);

alter table mh_account
    add     COMPANY_ID     NUMERIC(10, 0);

CREATE INDEX mh_account_company_id_idx
    ON mh_account (COMPANY_ID);

insert into mh_company
(id, version, name, params)
VALUES
(1, 0, 'main company', ''),
(2, 0, 'default company', '');

update mh_account
set COMPANY_ID = 2;

alter table mh_plan
    add    COMPANY_ID     NUMERIC(10, 0);

update mh_plan
set COMPANY_ID = 2;

alter table mh_plan alter column COMPANY_ID set not null;

alter table mh_atlas
    add     COMPANY_ID     NUMERIC(10, 0);

update mh_atlas
set COMPANY_ID = 2;

alter table mh_atlas
    modify COMPANY_ID int unsigned not null;

alter table mh_batch
    add     COMPANY_ID     NUMERIC(10, 0);

update mh_batch
set COMPANY_ID = 2;

alter table mh_batch alter column COMPANY_ID set not null;

alter table mh_batch
    add     ACCOUNT_ID     NUMERIC(10, 0);

update mh_batch
set ACCOUNT_ID = (select min(id) from mh_account );

alter table mh_batch alter column ACCOUNT_ID set not null;

alter table mh_event
    add     COMPANY_ID     NUMERIC(10, 0);

update mh_event
set COMPANY_ID = 2;

alter table mh_account drop column TOKEN;

alter table mh_account
    add     SECRET_KEY      varchar(25);

alter table mh_account
    add     TWO_FA      BOOLEAN not null default false;

