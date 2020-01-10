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
    ID      NUMERIC(10, 0) PRIMARY KEY,
    STUB    varchar(1) null
);

create table mh_gen_ids
(
    SEQUENCE_NAME       varchar(50) not null,
    SEQUENCE_NEXT_VALUE NUMERIC(10, 0)  NOT NULL
);

CREATE UNIQUE INDEX mh_gen_ids_sequence_name_unq_idx
    ON mh_gen_ids (SEQUENCE_NAME);

insert into mh_gen_ids
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
select 'mh_ids', max(UNIQUE_ID) from mh_company;