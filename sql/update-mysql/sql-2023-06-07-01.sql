--  Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU General Public License as published by
--  the Free Software Foundation, version 3 of the License.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU General Public License for more details.
--
--  You should have received a copy of the GNU General Public License
--  along with this program.  If not, see <https://www.gnu.org/licenses/>.

CREATE TABLE mh_variable_blob
(
    ID                  INT UNSIGNED    NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    VERSION             INT UNSIGNED    NOT NULL,
    VARIABLE_ID         NUMERIC(10, 0),
    DATA                LONGBLOB        not null
);


insert into mh_variable_blob
(VERSION, VARIABLE_ID, DATA)
select VERSION, ID, DATA from mh_variable where IS_NULLIFIED=0;


alter table mh_variable
    add VARIABLE_BLOB_ID NUMERIC(10);


# noinspection SqlWithoutWhere
update mh_variable v
set v.VARIABLE_BLOB_ID = (
    select vb.id from mh_variable_blob vb where vb.VARIABLE_ID=v.ID
);


alter table mh_variable_blob drop column VARIABLE_ID;


alter table mh_variable drop column data;


