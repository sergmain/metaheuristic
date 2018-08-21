CREATE TABLE AIAI_LP_STATION (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  IP          VARCHAR(30),
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_LOG_DATA (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP,
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    MEDIUMTEXT not null
);

CREATE TABLE AIAI_LP_DATASET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME        VARCHAR(40)  NOT NULL,
  DESCRIPTION VARCHAR(250) NOT NULL,
  IS_EDITABLE   tinyint(1) not null default 1,
  CMD_ASSEMBLE         VARCHAR(250),
  DATASET_FILE         VARCHAR(250)
);

CREATE TABLE AIAI_LP_DATASET_GROUP (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  DATASET_ID  NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  GROUP_NUMBER  NUMERIC(3, 0) NOT NULL,
  DESCRIPTION VARCHAR(250),
  CMD         VARCHAR(250),
  IS_ID_GROUP tinyint(1) not null default 0,
  IS_FEATURE  tinyint(1) not null default 0,
  IS_LABEL    tinyint(1) not null default 0
  FEATURE_FILE         VARCHAR(250),
  STATUS     tinyint(1) not null default 0
);

CREATE TABLE AIAI_LP_DATASET_COLUMN (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  DATASET_GROUP_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME    VARCHAR(50),
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_LP_EXPERIMENT (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  DATASET_ID  NUMERIC(10, 0),
  NAME        VARCHAR(50)   NOT NULL,
  DESCRIPTION VARCHAR(250)  NOT NULL,
  EPOCH       VARCHAR(100)  NOT NULL,
  EPOCH_VARIANT tinyint(1),
  SEED          INT(10),
  NUMBER_OF_SEQUENCE          INT(10) not null default 0,
  IS_ALL_SEQUENCE_PRODUCED   tinyint(1) not null default 0,
  IS_LAUNCHED   tinyint(1) not null default 0,
  IS_STARTED    tinyint(1) not null default 0,
  CREATED_ON   bigint not null,
  LAUNCHED_ON   bigint not null
);

CREATE TABLE AIAI_LP_EXPERIMENT_HYPER_PARAMS (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  HYPER_PARAM_KEY    VARCHAR(50),
  HYPER_PARAM_VALUES  VARCHAR(250)
);

CREATE TABLE AIAI_LP_EXPERIMENT_SNIPPET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  SNIPPET_CODE   VARCHAR(100) NOT NULL,
  SNIPPET_TYPE   VARCHAR(20) not null,
  SNIPPET_ORDER  NUMERIC(3, 0) NOT NULL  default 0
);

CREATE TABLE AIAI_LP_EXPERIMENT_SEQUENCE (
  ID            INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PARAMS          MEDIUMTEXT not null,
  STATION_ID          NUMERIC(10, 0),
  ASSIGNED_ON   bigint,
  IS_COMPLETED  tinyint(1) not null default 0
);

CREATE TABLE AIAI_LP_SNIPPET (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME      VARCHAR(50) not null,
  SNIPPET_TYPE      VARCHAR(50) not null,
  SNIPPET_VERSION   VARCHAR(20) not null,
  FILENAME  VARCHAR(250) not null,
  CODE        MEDIUMTEXT not null,
  CHECKSUM    VARCHAR(200) NOT NULL,
  ENV         VARCHAR(50) not null
);

CREATE TABLE AIAI_LP_DATASET_PATH (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  DATASET_ID  NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  PATH_NUMBER NUMERIC(3, 0) NOT NULL,
  PATH        VARCHAR(200),
  REG_TS      TIMESTAMP NOT NULL,
  CHECKSUM    VARCHAR(200),
  IS_FILE     tinyint(1) not null default 1,
  IS_VALID    tinyint(1) not null default 0
);

--============

CREATE TABLE AIAI_S_ENV (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME        VARCHAR(50),
  DESCRIPTION VARCHAR(250)
);

CREATE TABLE AIAI_S_METADATA (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  META_KEY    VARCHAR(50),
  META_VALUE  VARCHAR(250)
);

CREATE UNIQUE INDEX AIAI_S_METADATA_UNQ_IDX
  ON AIAI_S_METADATA (META_KEY);

CREATE TABLE AIAI_S_EXPERIMENT_SEQUENCE (
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  EXPERIMENT_SEQUENCE_ID          NUMERIC(10, 0) NOT NULL,
  VERSION      NUMERIC(5, 0)  NOT NULL,
  CREATED_ON   bigint not null,
  LAUNCHED_ON  bigint,
  FINISHED_ON  bigint,
  PARAMS       MEDIUMTEXT not null,
  EXEC_STATUS       MEDIUMTEXT

);

