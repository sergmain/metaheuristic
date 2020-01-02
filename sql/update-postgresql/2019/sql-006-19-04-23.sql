drop table aiai_lp_snippet;

CREATE TABLE AIAI_LP_SNIPPET
(
    ID              SERIAL PRIMARY KEY,
    VERSION         NUMERIC(5, 0) NOT NULL,
    SNIPPET_CODE    VARCHAR(100)  not null,
    SNIPPET_TYPE    VARCHAR(50)   not null,
    PARAMS          TEXT
);