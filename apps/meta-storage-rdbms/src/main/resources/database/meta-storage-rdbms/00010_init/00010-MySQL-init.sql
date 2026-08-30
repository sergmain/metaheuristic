-- Meta storage schema for MySQL / MariaDB
-- to Claude - always use table-level CONSTRAINT ... UNIQUE

-- Table-based id generator, copied from MH's mh_gen_ids. Namespaced because a module dropped into
-- the dispatcher must not collide with MH's own mh_gen_ids table - Liquibase would fail on the
-- second CREATE. The mechanism is the point, not the name: @TableGenerator allocates ids portably,
-- so no IDENTITY / AUTO_INCREMENT / SERIAL appears anywhere and every dialect behaves the same.

create table META_STORAGE_RECORD_GEN_IDS
(
    SEQUENCE_NAME       varchar(50) not null,
    SEQUENCE_NEXT_VALUE bigint  NOT NULL
);

CREATE UNIQUE INDEX meta_storage_record_gen_ids_seq_name_unq_idx
    ON META_STORAGE_RECORD_GEN_IDS (SEQUENCE_NAME);

insert into META_STORAGE_RECORD_GEN_IDS
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
values ('meta_storage_record_ids', 0);

-- The meta storage itself, on the MAIN datasource. Same shape and same semantics as META_RECORD in
-- the SQLite implementation: BODY is the system of record, TYPE and REC_KEY are body fields
-- projected into indexed columns, and (BUCKET, TYPE, REC_KEY) is the natural key that makes a
-- replayed batch idempotent.
-- REC_KEY is 191 rather than 255: utf8mb4 is 4 bytes per char and the composite unique key must fit
-- InnoDB's 3072-byte index limit.

CREATE TABLE META_STORAGE_RECORD
(
    ID          bigint NOT NULL PRIMARY KEY,
    VERSION     INT NOT NULL,
    BUCKET      VARCHAR(50) NOT NULL,
    TYPE        VARCHAR(50) NOT NULL,
    REC_KEY     VARCHAR(191) NOT NULL,
    BODY        MEDIUMTEXT NOT NULL,
    GEN         bigint NOT NULL,
    UPDATED_AT  bigint NOT NULL,
    CONSTRAINT UK_META_STORAGE_RECORD UNIQUE (BUCKET, TYPE, REC_KEY)
);

CREATE INDEX IDX_META_STORAGE_RECORD_TYPE ON META_STORAGE_RECORD(BUCKET, TYPE, GEN);
