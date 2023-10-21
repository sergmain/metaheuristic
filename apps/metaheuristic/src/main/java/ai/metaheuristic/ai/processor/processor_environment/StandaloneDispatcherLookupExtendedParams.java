/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.processor_environment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlV1;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;

import static ai.metaheuristic.ai.Consts.STANDALONE_PORT_NUMBER;

@Slf4j
public class StandaloneDispatcherLookupExtendedParams extends DispatcherLookupExtendedParams {

    public StandaloneDispatcherLookupExtendedParams(String restPassword) {
        super(getDispatcherLookupExtendedParams(restPassword));
    }

    private static DispatcherLookupParamsYaml getDispatcherLookupExtendedParams(String restPassword) {
    /*
      - signatureRequired: true
        url: http://localhost:8080
        lookupType: direct
        authType: basic
        restUsername: rest_user
        restPassword: 123
        taskProcessingTime: |
          workingDay: 0:00-23:59
          weekend: 0:00-23:59
        publicKey: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiEOUe8E8p9Xin0ikGzwFYc7NJK25n8K6LcLivIjs+CPBkgcOMsyncIDKmBqw8GYyhUl4I6KhE7TQSdedgul9a0B9/rfgX49b+nS0U7ObIK2sdJDzHh32ne7moX34L3zCPPciJ8M7GYQuCjrTKaRB8RUaG5nKYk9wHnzSm53Pq5nsmIvGsHBsx901OXpdDpkB2VB2Hsgu8vMxNvtprAD4x9z+QR01jG94z7JIN1zROZ0xS6uFF9IjxzsXNudRaELobRMfhqfyYMB8c3VNqsJ/vjlG6m7uMrinHnqvSjBPffueAay19J06P6IpEJ1LqeQdF8fygL5SnspjusnY60QzZwIDAQAB
        disabled: false
        priority: 1
    */
        DispatcherLookupParamsYamlV1.DispatcherLookupV1 lookup = new DispatcherLookupParamsYamlV1.DispatcherLookupV1();
        DispatcherLookupParamsYamlV1 dispatcherLookupConfig = new DispatcherLookupParamsYamlV1();
        dispatcherLookupConfig.dispatchers.add(lookup);


        lookup.signatureRequired = false;
        lookup.url = "http://localhost:" + STANDALONE_PORT_NUMBER;
//        lookup.assetManagerUrl = "http://localhost:" + STANDALONE_PORT_NUMBER;
        lookup.disabled = false;
        lookup.lookupType = Enums.DispatcherLookupType.direct;
        lookup.authType = EnumsApi.AuthType.basic;
        lookup.restUsername = Consts.REST_USER;
        lookup.restPassword = restPassword;
        lookup.taskProcessingTime = "";
        lookup.publicKey = null;
        lookup.priority = 1;

        final String s = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.toString(dispatcherLookupConfig);
        DispatcherLookupParamsYaml finalConfig  = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(s);

        return finalConfig;
    }

}
