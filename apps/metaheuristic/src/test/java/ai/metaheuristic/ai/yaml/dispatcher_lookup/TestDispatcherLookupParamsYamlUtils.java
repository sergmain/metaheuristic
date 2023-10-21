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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.Consts.STANDALONE_PORT_NUMBER;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 8/19/2023
 * Time: 9:20 PM
 */
public class TestDispatcherLookupParamsYamlUtils {

    @Test
    public void test_DispatcherLookupParamsYamlUtils() {
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
        lookup.restPassword = "123";
        lookup.taskProcessingTime = "";
        lookup.publicKey = null;
        lookup.priority = 1;

        final String s = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.toString(dispatcherLookupConfig);
        DispatcherLookupParamsYaml finalConfig  = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertEquals(1, finalConfig.assetManagers.size());
    }
}
