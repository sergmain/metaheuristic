/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

INSERT INTO mhbp_api (ID, VERSION, COMPANY_ID, ACCOUNT_ID, CREATED_ON, NAME, CODE, DISABLED, SCHEME, MODEL)
VALUES
    (1, 2, 2, 2, 1641635739675, 'question', 'question', 0, 'api:
  authType: basic
  code: question
  basicAuth:
    username: q
    password: \'123\'
version: 1
', 'version: 1
code: simple-provider-localhost:1.0
authType: basic
model:
  basicAuth:
    usernameParam: password
    passwordParam: password
  baseMeta:
    uri: http://localhost:8080/rest/v1/provider/simple/stub
    attrs:
      - object: question
        param: q
  metas:
    - meta:
        object: question
        desc: Question to ask
        uri: /question
      response:
        asText: true
');



INSERT INTO mhbp_api (VERSION, COMPANY_ID, ACCOUNT_ID, CREATED_ON, NAME, CODE, DISABLED, SCHEME, MODEL)
VALUES
    (1, 2, 2, 1641635739675, 'question', 'question', 0, '', '');
commit