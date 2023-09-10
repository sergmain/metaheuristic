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

package ai.metaheuristic.ai.mhbp.api_keys;

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuth;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 9/10/2023
 * Time: 4:40 AM
 */
@Execution(CONCURRENT)
public class ApiKeysProviderUtilsTest {

    @Test
    public void test_getTokenFromEnvironment() {
        assertEquals("aaa", ApiKeysProviderUtils.getTokenFromEnvironment((key)->"aaa", "$OPENAI_API_KEY$"));
        assertEquals("aaa", ApiKeysProviderUtils.getTokenFromEnvironment((key)->"aaa", "$OPENAI_API_KEY"));
        assertEquals("aaa", ApiKeysProviderUtils.getTokenFromEnvironment((key)->"aaa", "%OPENAI_API_KEY%"));
        assertEquals("aaa", ApiKeysProviderUtils.getTokenFromEnvironment((key)->"aaa", "%OPENAI_API_KEY"));
    }

    @Test
    public void test_getActualToken_1() {
        String yaml = """
            version: 2
            auth:
              code: openai
              type: token
              token:
                place: header
                key: OPENAI_API_KEY
            """;

        ApiAuth apiAuth = ApiAuthUtils.UTILS.to(yaml);

        Account acc = new Account();
        AccountParamsYaml params = new AccountParamsYaml();
        params.openaiKey = "123";
        acc.updateParams(params);
        String token = ApiKeysProviderUtils.getActualToken(apiAuth.auth, ()-> acc, (key)-> {
            //noinspection ReturnOfNull
            return null;
        });
        assertEquals("123", token);
    }

    @Test
    public void test_getActualToken_2() {
        String yaml = """
            version: 2
            auth:
              code: openai
              type: token
              token:
                place: header
                env: "%OPENAI_API_KEY%"
            """;

        ApiAuth apiAuth = ApiAuthUtils.UTILS.to(yaml);

        Account acc = new Account();
        AccountParamsYaml params = new AccountParamsYaml();
        acc.updateParams(params);
        String token = ApiKeysProviderUtils.getActualToken(apiAuth.auth, ()-> acc, (key)-> {
            //noinspection ReturnOfNull
            return "OPENAI_API_KEY".equals(key) ? "123" : null;
        });
        assertEquals("123", token);
    }
}
