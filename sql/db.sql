CREATE TABLE AIAI_IDS (
  SEQUENCE_NAME       VARCHAR(50),
  SEQUENCE_NEXT_VALUE DECIMAL(10)
);

CREATE UNIQUE INDEX AIAI_IDS_SEQUENCE_NAME_IDX
  ON AIAI_IDS (SEQUENCE_NAME);

CREATE UNIQUE INDEX AIAI_IDS_SEQUENCE_NAME_NEXT_VAL
  ON AIAI_IDS
  (SEQUENCE_NAME, SEQUENCE_NEXT_VALUE);

CREATE TABLE AIAI_STATION (
  ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  IP          VARCHAR(30),
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_DATASET (
  ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_DATASET_GROUP (
  ID          NUMERIC(10, 0) NOT NULL,
  DATASET_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  GROUP_NUMBER  NUMERIC(3, 0) NOT NULL,
  DESCRIPTION VARCHAR(250),
  IS_SKIP   tinyint(1) not null default 0
);

CREATE TABLE AIAI_DATASET_COLUMN (
  ID          NUMERIC(10, 0) NOT NULL,
  DATASET_GROUP_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME    VARCHAR(50),
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_DATASET (
  ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_EXPERIMENT (
  ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME        VARCHAR(50),
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_ENV (
  ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME        VARCHAR(50),
  DESCRIPTION VARCHAR(250)
);
