delete from mh_data where DATA_TYPE in (3, 4);

CREATE TABLE mh_snippet_data
(
    ID              INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION         INT UNSIGNED    NOT NULL,
    SNIPPET_CODE    VARCHAR(100) not null,
    UPLOAD_TS       TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    DATA            LONGBLOB,
    PARAMS          MEDIUMTEXT not null
);

CREATE UNIQUE INDEX mh_snippet_data_snippet_code_unq_idx
    ON mh_snippet_data (SNIPPET_CODE);

insert into mh_snippet_data
(ID, VERSION, SNIPPET_CODE, UPLOAD_TS, DATA, PARAMS)
select ID, VERSION, CODE, UPLOAD_TS, DATA, PARAMS
from mh_data
where DATA_TYPE=2;

commit;

delete from mh_data where DATA_TYPE =2;

commit;

