-- Create MH_META_STORAGE table for PostgreSQL
-- to Claude - always use table-level CONSTRAINT ... UNIQUE

-- Runtime-typed record store. TYPE is a column VALUE, never an enum: a new kind of thing is a new
-- string and needs no DDL, no recompile and no restart.
-- BODY is the system of record and is OPAQUE to MH - it is never parsed here. TYPE and REC_KEY are
-- caller-supplied identity, projected into indexed columns.
-- (COMPANY_ID, TYPE, REC_KEY) is the natural key: a replayed batch upserts onto the same rows
-- instead of appending duplicates, which is what makes an effectful task safe to retry.
-- The id comes from mh_gen_ids via @TableGenerator - no IDENTITY / AUTO_INCREMENT / SERIAL, so the
-- same DDL behaves identically on every dialect.

CREATE TABLE MH_META_STORAGE
(
    ID          bigint NOT NULL PRIMARY KEY,
    VERSION     INT NOT NULL,
    COMPANY_ID  bigint NOT NULL,
    TYPE        VARCHAR(50) NOT NULL,
    REC_KEY     VARCHAR(255) NOT NULL,
    BODY        TEXT NOT NULL,
    GEN         bigint NOT NULL,
    UPDATED_AT  bigint NOT NULL,
    CONSTRAINT UK_MH_META_STORAGE UNIQUE (COMPANY_ID, TYPE, REC_KEY)
);

CREATE INDEX IDX_MH_META_STORAGE_TYPE ON MH_META_STORAGE(COMPANY_ID, TYPE, GEN);

insert into mh_gen_ids
(SEQUENCE_NAME, SEQUENCE_NEXT_VALUE)
values ('mh_meta_storage_ids', 0);
