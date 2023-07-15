-- ===============  DO NOT UPDATE THIS COPYRIGHT =========================
--  Metaheuristic, Copyright (C) 2017-20xx, Innovation platforms, LLC
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


-- !!!!! names of tables must be in lower case !!!!!!

alter table mh_account add PARAMS MEDIUMTEXT null;

alter table mh_exec_context add ACCOUNT_ID INT UNSIGNED;

