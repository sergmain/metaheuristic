-- ==================== Pilot part ==========================

create table PILOT_BATCH
(
  ID               INT(10)        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  VERSION          NUMERIC(5, 0)  NOT NULL,
  FLOW_ID          NUMERIC(10, 0) NOT NULL,
  CREATED_ON       bigint         NOT NULL
);

CREATE TABLE PILOT_BATCH_FLOW_INSTANCE
(
  ID          INT(10) NOT NULL AUTO_INCREMENT  PRIMARY KEY,
  VERSION     NUMERIC(5, 0)  NOT NULL,
  BATCH_ID    NUMERIC(10, 0) NOT NULL,
  FLOW_INSTANCE_ID NUMERIC(10, 0) NOT NULL
);
