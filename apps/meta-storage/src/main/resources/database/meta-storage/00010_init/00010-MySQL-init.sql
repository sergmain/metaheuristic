-- Create META_STORAGE_STUB table for MySQL / MariaDB
-- Stub table. It exists so the module carries a real Liquibase-managed schema on the MAIN
-- datasource, alongside the SQLite file the meta storage itself lives in. The two are separate
-- stores on purpose; see MetaStorageConfig for why only one of them is a Spring DataSource bean.

CREATE TABLE META_STORAGE_STUB (
    ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    VERSION INT NOT NULL DEFAULT 0,
    COMPANY_ID BIGINT NOT NULL,
    CODE VARCHAR(50) NOT NULL,
    PARAMS MEDIUMTEXT NOT NULL,
    CREATED_ON BIGINT NOT NULL,
    CONSTRAINT UK_META_STORAGE_STUB_CODE UNIQUE (COMPANY_ID, CODE)
);

CREATE INDEX IDX_META_STORAGE_STUB_COMPANY ON META_STORAGE_STUB(COMPANY_ID);
