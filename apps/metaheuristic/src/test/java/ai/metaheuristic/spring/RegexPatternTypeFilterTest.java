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

package ai.metaheuristic.spring;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 6/27/2023
 * Time: 9:36 PM
 */
public class RegexPatternTypeFilterTest {

    @Test
    public void test_() {

        Pattern p = Pattern.compile("ai\\.metaheuristic\\.ai\\.yaml\\..*");

        assertFalse(p.matcher("ai.metaheuristic.ai.dispatcher.repositories.AccountRepository").find());
        assertTrue(p.matcher("ai.metaheuristic.ai.yaml.batch.BatchParamsYaml").find());
    }
}
