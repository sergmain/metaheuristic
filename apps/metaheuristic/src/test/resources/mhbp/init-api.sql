/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
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