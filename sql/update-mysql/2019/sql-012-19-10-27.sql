CREATE TABLE mh_company
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    NAME            VARCHAR(50)   NOT NULL,
    PARAMS          MEDIUMTEXT null
);

alter table mh_account
    add     COMPANY_ID      INT UNSIGNED;

CREATE INDEX mh_account_company_id_idx
    ON mh_account (COMPANY_ID);

insert into mh_company
(id, version, name, params)
VALUES
(1, 0, 'main company', ''),
(2, 0, 'default company', '');

update mh_account
set COMPANY_ID = 2;

alter table mh_source_code
    add     COMPANY_ID      INT UNSIGNED;

update mh_source_code
set COMPANY_ID = 2;

alter table mh_source_code modify COMPANY_ID int unsigned not null;

alter table mh_atlas
    add     COMPANY_ID      INT UNSIGNED;

update mh_atlas
set COMPANY_ID = 2;

alter table mh_atlas modify COMPANY_ID int unsigned not null;

alter table mh_batch
    add     COMPANY_ID      INT UNSIGNED;

update mh_batch
set COMPANY_ID = 2;

alter table mh_batch modify COMPANY_ID int unsigned not null;

alter table mh_batch
    add     ACCOUNT_ID      INT UNSIGNED;

update mh_batch
set ACCOUNT_ID = (select min(id) from mh_account );

alter table mh_batch modify ACCOUNT_ID int unsigned not null;

alter table mh_event
    add     COMPANY_ID      INT UNSIGNED;

update mh_event
set COMPANY_ID = 2;

alter table mh_account drop column TOKEN;

alter table mh_account
    add     SECRET_KEY      varchar(25);

alter table mh_account
    add     TWO_FA      BOOLEAN not null default false;

