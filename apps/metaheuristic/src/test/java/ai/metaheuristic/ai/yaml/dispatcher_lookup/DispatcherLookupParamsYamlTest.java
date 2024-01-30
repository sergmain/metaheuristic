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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 10/26/2023
 * Time: 8:17 PM
 */
@Execution(CONCURRENT)
public class DispatcherLookupParamsYamlTest {

    @Test
    public void test_checkIntegrity_55() {
        String yaml = """
            version: 2
            dispatchers:
              - signatureRequired: true
                url: http://localhost:8889
                lookupType: direct
                authType: basic
                restUsername: qqq
                restPassword: 123
                assetManagerUrl:
                disabled: false
            assetManagers:
              - url: https://localhost:8889
                username: rest_user
                password: 123
            """;

        Throwable th = assertThrows(CheckIntegrityFailedException.class, ()->DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
        assertTrue(th.getMessage().startsWith("050.040"));
    }

    @Test
    public void test_checkIntegrity_60() {
        String yaml = """
            version: 2
            dispatchers:
              - signatureRequired: true
                url: http://localhost:8889
                lookupType: direct
                authType: basic
                restUsername: qqq
                restPassword: 123
                assetManagerUrl: http://localhost:8889
                disabled: false
            assetManagers:
              - url:
                username: rest_user
                password: 123
            """;

        Throwable th = assertThrows(CheckIntegrityFailedException.class, ()->DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
        assertTrue(th.getMessage().startsWith("050.080"));

    }
    @Test
    public void test_checkIntegrity_70() {
        String yaml = """
            version: 2
            dispatchers:
              - signatureRequired: true
                url: http://localhost:8889
                lookupType: direct
                authType: basic
                restUsername: qqq
                restPassword: 123
                assetManagerUrl: http://localhost:8889
                disabled: false
            assetManagers:
              - url: https://localhost:8889
                username: rest_user
                password: 123
            """;

        Throwable th = assertThrows(CheckIntegrityFailedException.class, ()->DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
        assertTrue(th.getMessage().startsWith("050.120"));

    }
}
