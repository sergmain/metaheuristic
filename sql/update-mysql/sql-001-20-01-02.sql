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
    ID int unsigned NOT NULL PRIMARY KEY,
    STUB varchar(1) null
);

create table mh_gen_ids
(
    SEQUENCE_NAME       varchar(50) not null,
    SEQUENCE_NEXT_VALUE NUMERIC(10, 0)  NOT NULL
);

CREATE UNIQUE INDEX mh_gen_ids_sequence_name_unq_idx
    ON mh_gen_ids (SEQUENCE_NAME);

insert mh_gen_ids
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
select 'mh_ids', max(UNIQUE_ID) from mh_company;

alter table mh_account
    add     UPDATED_ON  bigint;

update mh_account
set UPDATED_ON = CREATED_ON;

alter table mh_account modify UPDATED_ON  bigint not null;