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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextUtils.buildTaskContextId, getCurrTaskContextIdForSubProcesses
 * and VariableUtils.getParentContext - baseline before Option 5d refactoring.
 *
 * @author Sergio Lissner
 * Date: 2/16/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestContextUtilsExtended {

    // === buildTaskContextId ===

    @Test
    public void test_buildTaskContextId_simple() {
        // top-level sub-process context, instance 0
        String result = ContextUtils.buildTaskContextId("1,2", "0");
        assertEquals("1,2#0", result);
    }

    @Test
    public void test_buildTaskContextId_multipleInstances() {
        assertEquals("1,2#1", ContextUtils.buildTaskContextId("1,2", "1"));
        assertEquals("1,2#2", ContextUtils.buildTaskContextId("1,2", "2"));
        assertEquals("1,2#100", ContextUtils.buildTaskContextId("1,2", "100"));
    }

    @Test
    public void test_buildTaskContextId_deepContext() {
        // 4th level nesting
        String result = ContextUtils.buildTaskContextId("1,2,3,4", "5");
        assertEquals("1,2,3,4#5", result);
    }

    // === getCurrTaskContextIdForSubProcesses ===

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_topLevel() {
        // parent task at top level "1", sub-process at level "1,2"
        // path of "1" is null -> result is just the processContextId
        String result = ContextUtils.getCurrTaskContextIdForSubProcesses("1", "1,2");
        assertEquals("1,2", result);
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_withPath() {
        // parent task at "1,2#3", sub-process level "1,2,4"
        // path of "1,2#3" is "3" -> result is "1,2,4,3"
        String result = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#3", "1,2,4");
        assertEquals("1,2,4,3", result);
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_deeperPath() {
        // parent context has deeper path
        // "1,2,3#5" -> level="1,2,3", path="5" -> result = "1,2,4" + "," + "5" = "1,2,4,5"
        String result = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,3#5", "1,2,4");
        assertEquals("1,2,4,5", result);
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_noSubContext() {
        // parent task at "1,2" (no #N suffix), sub-process "1,2,3"
        // path of "1,2" is null -> result is just processContextId
        String result = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2", "1,2,3");
        assertEquals("1,2,3", result);
    }

    // === getLevel and getPath ===

    @Test
    public void test_getLevel_withHash() {
        assertEquals("1,2", ContextUtils.getLevel("1,2#3"));
        assertEquals("1,2,3,4", ContextUtils.getLevel("1,2,3,4#99"));
    }

    @Test
    public void test_getLevel_withoutHash() {
        assertEquals("1", ContextUtils.getLevel("1"));
        assertEquals("1,2,3", ContextUtils.getLevel("1,2,3"));
    }

    @Test
    public void test_getPath_withHash() {
        assertEquals("3", ContextUtils.getPath("1,2#3"));
        assertEquals("99", ContextUtils.getPath("1,2,3,4#99"));
    }

    @Test
    public void test_getPath_withoutHash() {
        assertNull(ContextUtils.getPath("1"));
        assertNull(ContextUtils.getPath("1,2,3"));
    }

    // === VariableUtils.getParentContext - safety net ===

    @Test
    public void test_getParentContext_topLevel() {
        // "1" has no parent
        assertNull(VariableUtils.getParentContext("1"));
    }

    @Test
    public void test_getParentContext_twoLevels() {
        assertEquals("1", VariableUtils.getParentContext("1,2"));
    }

    @Test
    public void test_getParentContext_threeLevels() {
        assertEquals("1,2", VariableUtils.getParentContext("1,2,3"));
    }

    @Test
    public void test_getParentContext_fourLevels() {
        assertEquals("1,2,3", VariableUtils.getParentContext("1,2,3,4"));
    }

    @Test
    public void test_getParentContext_fiveLevels() {
        assertEquals("1,2,3,4", VariableUtils.getParentContext("1,2,3,4,5"));
    }

    @Test
    public void test_getParentContext_withHashSuffix() {
        // "1,2#3" - the comma is between "1" and "2", not before "#3"
        // lastIndexOf(',') returns index of comma between 1 and 2
        assertEquals("1", VariableUtils.getParentContext("1,2#3"));
    }

    @Test
    public void test_getParentContext_deepWithHash() {
        assertEquals("1,2,3", VariableUtils.getParentContext("1,2,3,4#7"));
    }

    // === variable scoping chain walk ===

    @Test
    public void test_parentContextChain_walksUpCorrectly() {
        // simulate findVariableInAllInternalContexts walking up
        String ctx = "1,2,3,4,5";

        String parent1 = VariableUtils.getParentContext(ctx);
        assertEquals("1,2,3,4", parent1);

        String parent2 = VariableUtils.getParentContext(parent1);
        assertEquals("1,2,3", parent2);

        String parent3 = VariableUtils.getParentContext(parent2);
        assertEquals("1,2", parent3);

        String parent4 = VariableUtils.getParentContext(parent3);
        assertEquals("1", parent4);

        String parent5 = VariableUtils.getParentContext(parent4);
        assertNull(parent5);
    }
}
