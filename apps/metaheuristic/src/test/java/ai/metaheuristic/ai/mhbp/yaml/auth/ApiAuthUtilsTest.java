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

package ai.metaheuristic.ai.mhbp.yaml.auth;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static ai.metaheuristic.ai.Enums.TokenPlace.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 9/10/2023
 * Time: 3:33 AM
 */
@Execution(CONCURRENT)
public class ApiAuthUtilsTest {

    @Test
    public void test_to_1() {

        String yaml = """
            version: 1
            auth:
              code: openai
              type: token
              token:
                place: header
                env: $OPENAI_API_KEY$
            """;

        ApiAuth v = ApiAuthUtils.UTILS.to(yaml);

        assertEquals("openai", v.auth.code);
        assertEquals(Enums.AuthType.token, v.auth.type);
        assertNotNull(v.auth.token);
        assertEquals(header, v.auth.token.place);
        assertEquals("$OPENAI_API_KEY$", v.auth.token.env);
    }

    @Test
    public void test_to_2() {

        String yaml = """
            version: 2
            auth:
              code: openai
              type: token
              token:
                place: header
                key: OPENAI_API_KEY
            """;

        ApiAuth v = ApiAuthUtils.UTILS.to(yaml);

        assertEquals("openai", v.auth.code);
        assertEquals(Enums.AuthType.token, v.auth.type);
        assertNotNull(v.auth.token);
        assertEquals(header, v.auth.token.place);
        assertEquals("OPENAI_API_KEY", v.auth.token.key);
    }
}
