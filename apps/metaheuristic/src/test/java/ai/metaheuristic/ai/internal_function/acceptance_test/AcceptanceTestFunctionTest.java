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

package ai.metaheuristic.ai.internal_function.acceptance_test;

import ai.metaheuristic.ai.dispatcher.internal_functions.acceptance_test.AcceptanceTestFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 5/25/2023
 * Time: 4:20 PM
 */
public class AcceptanceTestFunctionTest {

    @Test
    public void test_validateAnswer() {
        assertFalse(AcceptanceTestFunction.validateAnswer("Yes || yes || Yes. || yes.", "No"));

        assertTrue(AcceptanceTestFunction.validateAnswer("Yes || yes || Yes. || yes.", "Yes"));
        assertTrue(AcceptanceTestFunction.validateAnswer("Yes || yes || Yes. || yes.", "Yes."));
        assertTrue(AcceptanceTestFunction.validateAnswer("Yes || yes || Yes. || yes.", "  yes.  "));
        assertTrue(AcceptanceTestFunction.validateAnswer("Yes || yes || Yes. || yes.", "  yes  "));
    }
}
