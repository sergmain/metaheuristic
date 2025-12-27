/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.data;

import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.mhbp.data.ScenarioData.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 5/30/2023
 * Time: 3:32 PM
 */
public class ScenarioDataTest {

    @SuppressWarnings({"ConstantValue", "SimplifiableAssertion"})
    @Test
    public void test_SimpleScenarioStep_equals() {
        SimpleScenarioStep step1 = new SimpleScenarioStep("uuid1");
        assertFalse(step1.equals(null));

        SimpleScenarioStep step2 = new SimpleScenarioStep("uuid1");
        assertTrue(step1.equals(step2));

        step2.uuid = "uuid2";
        assertFalse(step1.equals(step2));

        step1.parentUuid = "uuid1";
        step2.uuid = "uuid1";
        step2.parentUuid = "uuid1";
        assertTrue(step1.equals(step2));

        step2.parentUuid = "uuid2";
        assertFalse(step1.equals(step2));

    }
}
