truncate table mh_exec_context;

alter table mh_exec_context
    add CTX_GRAPH_ID            INT UNSIGNED default NULL;

alter table mh_exec_context
    add CTX_TASK_STATE_ID       INT UNSIGNED default NULL;

alter table mh_exec_context
    add CTX_VARIABLE_INFO_ID    INT UNSIGNED default NULL;

CREATE TABLE mh_exec_context_graph
(
    ID                  INT UNSIGNED    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    VERSION             INT UNSIGNED    NOT NULL,
    EXEC_CONTEXT_ID     INT UNSIGNED    NOT NULL,
    PARAMS              LONGTEXT NOT NULL
);

CREATE TABLE mh_exec_context_task_state
(
    ID                  INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             INT UNSIGNED    NOT NULL,
    EXEC_CONTEXT_ID     INT UNSIGNED    NOT NULL,
    PARAMS              LONGTEXT NOT NULL
);

CREATE TABLE mh_exec_context_variable_info
(
    ID                  INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             INT UNSIGNED    NOT NULL,
    EXEC_CONTEXT_ID     INT UNSIGNED    NOT NULL,
    PARAMS              LONGTEXT NOT NULL
);



