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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 12/27/2020
 * Time: 8:20 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestSourceCodeSelector {

    @Test
    public void test() {
        assertTrue(SourceCodeSelectorService.getGroups("").isEmpty());
        assertTrue(SourceCodeSelectorService.getGroups(" ").isEmpty());
        Set<String> set;
        set = SourceCodeSelectorService.getGroups("aaa");
        assertFalse(set.isEmpty());
        assertTrue(set.contains("aaa"));

        set = SourceCodeSelectorService.getGroups("aaa,bbb");
        assertFalse(set.isEmpty());
        assertTrue(set.contains("aaa"));
        assertTrue(set.contains("bbb"));

        set = SourceCodeSelectorService.getGroups("aaa, bbb");
        assertFalse(set.isEmpty());
        assertTrue(set.contains("aaa"));
        assertTrue(set.contains("bbb"));
    }
}
