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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 12:38 AM
 */
public class TokenProviderTest {

    @Test
    public void test_getEnvParamName() {
        assertEquals("A", ApiKeysProvider.getEnvParamName("A"));
        assertEquals("A", ApiKeysProvider.getEnvParamName("%A"));
        assertEquals("A", ApiKeysProvider.getEnvParamName("%A%"));
        assertEquals("A", ApiKeysProvider.getEnvParamName("$A"));
        assertEquals("A", ApiKeysProvider.getEnvParamName("$A$"));
    }
}
