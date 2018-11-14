CREATE TABLE AIAI_LP_STATION (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  IP          VARCHAR(30),
  UPDATE_TS   TIMESTAMP DEFAULT to_timestamp(0),
  DESCRIPTION VARCHAR(250),
  ENV       TEXT,
  ACTIVE_TIME VARCHAR(250)
);

CREATE TABLE AIAI_LOG_DATA (
  ID          SERIAL PRIMARY KEY,
  REF_ID      NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT to_timestamp(0),
  LOG_TYPE    NUMERIC(5, 0)  NOT NULL,
  LOG_DATA    TEXT not null
);

CREATE TABLE AIAI_LP_DATA (
  ID          SERIAL PRIMARY KEY,
  CODE        VARCHAR(100),
  POOL_CODE   VARCHAR(250),
  DATA_TYPE   NUMERIC(2, 0) NOT NULL,
  VERSION     NUMERIC(5, 0) NOT NULL,
  UPDATE_TS   TIMESTAMP DEFAULT to_timestamp(0),
  DATA        OID,
  CHECKSUM    VARCHAR(2048),
  IS_VALID    BOOLEAN not null default false
);

CREATE UNIQUE INDEX AIAI_LP_DATA_CODE_UNQ_IDX
  ON AIAI_LP_DATA (CODE);

CREATE TABLE AIAI_LP_EXPERIMENT (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  FLOW_INSTANCE_ID  NUMERIC(10, 0) NOT NULL,
  NAME        VARCHAR(50)   NOT NULL,
  DESCRIPTION VARCHAR(250)  NOT NULL,
  CODE        VARCHAR(50)   NOT NULL,
  EPOCH       VARCHAR(100)  NOT NULL,
  EPOCH_VARIANT smallint  NOT NULL,
  SEED          integer,
  NUMBER_OF_TASK         integer not null default 0,
  IS_ALL_TASK_PRODUCED   BOOLEAN not null default false,
  IS_FEATURE_PRODUCED   BOOLEAN not null default false,
  IS_LAUNCHED   BOOLEAN not null default false,
  EXEC_STATE        smallint not null default 0,
  CREATED_ON   bigint not null,
  LAUNCHED_ON   bigint
);

CREATE INDEX AIAI_LP_EXPERIMENT_IS_LAUNCHED_EXEC_STATE_IDX
  ON AIAI_LP_EXPERIMENT (IS_LAUNCHED, EXEC_STATE);


CREATE TABLE AIAI_LP_EXPERIMENT_HYPER_PARAMS (
  ID          SERIAL PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  HYPER_PARAM_KEY    VARCHAR(50),
  HYPER_PARAM_VALUES  VARCHAR(250)
);

CREATE TABLE AIAI_LP_EXPERIMENT_FEATURE (
  ID          SERIAL PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  RESOURCE_CODES   VARCHAR(2048) not null,
  IS_IN_PROGRESS    BOOLEAN not null default false,
  IS_FINISHED   BOOLEAN not null default false,
  EXEC_STATUS  smallint not null default 0
);

CREATE UNIQUE INDEX AIAI_LP_EXPERIMENT_RESOURCE_CODES_UNQ_IDX
  ON AIAI_LP_EXPERIMENT_FEATURE (EXPERIMENT_ID, RESOURCE_CODES);

CREATE TABLE AIAI_LP_EXPERIMENT_SNIPPET (
  ID          SERIAL PRIMARY KEY,
  EXPERIMENT_ID          NUMERIC(10, 0) NOT NULL,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  SNIPPET_CODE   VARCHAR(100) NOT NULL,
  SNIPPET_TYPE   VARCHAR(20) not null
);

CREATE INDEX AIAI_LP_EXPERIMENT_SNIPPET_EXPERIMENT_ID_IDX
  ON AIAI_LP_EXPERIMENT_SNIPPET (EXPERIMENT_ID);

CREATE TABLE AIAI_LP_TASK (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  PARAMS          TEXT not null,
  STATION_ID          NUMERIC(10, 0),
  ASSIGNED_ON   bigint,
  IS_COMPLETED  BOOLEAN default false not null ,
  COMPLETED_ON   bigint,
  SNIPPET_EXEC_RESULTS  TEXT,
  METRICS      TEXT,
  TASK_ORDER   smallint not null,
  FLOW_INSTANCE_ID          NUMERIC(10, 0)   NOT NULL
);

CREATE TABLE AIAI_LP_SNIPPET (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  NAME      VARCHAR(50) not null,
  SNIPPET_TYPE      VARCHAR(50) not null,
  SNIPPET_VERSION   VARCHAR(20) not null,
  FILENAME  VARCHAR(250) not null,
  CHECKSUM    VARCHAR(2048),
  IS_SIGNED   BOOLEAN not null default false,
  IS_REPORT_METRICS   BOOLEAN not null default false,
  ENV         VARCHAR(50) not null,
  PARAMS         VARCHAR(1000),
  CODE_LENGTH integer not null,
  IS_FILE_PROVIDED   BOOLEAN not null default false
);

CREATE UNIQUE INDEX AIAI_LP_SNIPPET_UNQ_IDX
  ON AIAI_LP_SNIPPET (NAME, SNIPPET_VERSION);

CREATE TABLE AIAI_LP_ENV (
  ID          SERIAL PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  ENV_KEY     VARCHAR(50)  NOT NULL,
  ENV_VALUE   VARCHAR(500)  NOT NULL,
  SIGNATURE   varchar(1000)
);

CREATE UNIQUE INDEX AIAI_LP_ENV_UNQ_IDX
  ON AIAI_LP_ENV (ENV_KEY);

CREATE TABLE AIAI_LP_FLOW (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  CODE      varchar(50)  NOT NULL,
  PARAMS        TEXT not null
);

CREATE TABLE AIAI_LP_FLOW_INSTANCE (
  ID            SERIAL PRIMARY KEY,
  VERSION       NUMERIC(5, 0)  NOT NULL,
  FLOW_ID       NUMERIC(10, 0) NOT NULL,
  IS_COMPLETED  BOOLEAN not null default false,
  COMPLETED_ON  bigint,
  INPUT_POOL_CODE  varchar(50) NOT NULL
);

