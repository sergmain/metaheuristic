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
 * Tests for ContextUtils with '|' format for unambiguous parent derivation at any nesting depth.
 *
 * Format: processContextId[|ancestorInstance]*#instanceNumber
 *   processContextId: comma-separated DAG path (e.g., "1,2,5")
 *   ancestorInstance: single integer, separated by '|'
 *   instanceNumber: fan-out instance, separated by '#'
 *
 * Examples:
 *   "1"              - top-level, no fan-out
 *   "1,2#1"          - first-level fan-out, instance 1
 *   "1,2,5|1#0"      - second-level, ancestor instance 1
 *   "1,2,5,6|1|0#0"  - third-level, ancestors: grandparent=1, parent=0
 *
 * @author Sergio Lissner
 * Date: 2/16/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestContextUtilsExtended {

    // === buildTaskContextId ===

    @Test
    public void test_buildTaskContextId_simple() {
        assertEquals("1,2#0", ContextUtils.buildTaskContextId("1,2", "0"));
    }

    @Test
    public void test_buildTaskContextId_multipleInstances() {
        assertEquals("1,2#1", ContextUtils.buildTaskContextId("1,2", "1"));
        assertEquals("1,2#2", ContextUtils.buildTaskContextId("1,2", "2"));
        assertEquals("1,2#100", ContextUtils.buildTaskContextId("1,2", "100"));
    }

    @Test
    public void test_buildTaskContextId_withAncestors() {
        assertEquals("1,2,5|1#0", ContextUtils.buildTaskContextId("1,2,5|1", "0"));
        assertEquals("1,2,5,6|1|0#0", ContextUtils.buildTaskContextId("1,2,5,6|1|0", "0"));
    }

    // === getCurrTaskContextIdForSubProcesses ===

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_topLevel() {
        assertEquals("1,2", ContextUtils.getCurrTaskContextIdForSubProcesses("1", "1,2"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_firstLevel() {
        // "1,2#1" -> path="1", no ancestors -> "1,2,5|1"
        assertEquals("1,2,5|1", ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_secondLevel() {
        // "1,2,5|1#0" -> path="0", ancestors="1" -> new ancestors="1|0"
        assertEquals("1,2,5,6|1|0", ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_thirdLevel() {
        // "1,2,5,6|1|0#2" -> path="2", ancestors="1|0" -> new ancestors="1|0|2"
        assertEquals("1,2,5,6,7|1|0|2", ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5,6|1|0#2", "1,2,5,6,7"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_noHash() {
        assertEquals("1,2,3", ContextUtils.getCurrTaskContextIdForSubProcesses("1,2", "1,2,3"));
    }

    // === getProcessContextId and getAncestorPath ===

    @Test
    public void test_getProcessContextId() {
        assertEquals("1,2", ContextUtils.getProcessContextId("1,2"));
        assertEquals("1,2,5", ContextUtils.getProcessContextId("1,2,5|1"));
        assertEquals("1,2,5,6", ContextUtils.getProcessContextId("1,2,5,6|1|0"));
    }

    @Test
    public void test_getAncestorPath() {
        assertNull(ContextUtils.getAncestorPath("1,2"));
        assertEquals("1", ContextUtils.getAncestorPath("1,2,5|1"));
        assertEquals("1|0", ContextUtils.getAncestorPath("1,2,5,6|1|0"));
        assertEquals("1|0|2", ContextUtils.getAncestorPath("1,2,5,6,7|1|0|2"));
    }

    // === getLevel and getPath ===

    @Test
    public void test_getLevel() {
        assertEquals("1,2", ContextUtils.getLevel("1,2#3"));
        assertEquals("1,2,5|1", ContextUtils.getLevel("1,2,5|1#0"));
        assertEquals("1,2,5,6|1|0", ContextUtils.getLevel("1,2,5,6|1|0#0"));
        assertEquals("1", ContextUtils.getLevel("1"));
    }

    @Test
    public void test_getPath() {
        assertEquals("3", ContextUtils.getPath("1,2#3"));
        assertEquals("0", ContextUtils.getPath("1,2,5|1#0"));
        assertNull(ContextUtils.getPath("1"));
    }

    // === deriveParentTaskContextId - root ===

    @Test
    public void test_deriveParent_root() {
        assertNull(ContextUtils.deriveParentTaskContextId("1"));
    }

    // === deriveParentTaskContextId - simple nested (no #) ===

    @Test
    public void test_deriveParent_simpleNested() {
        assertEquals("1", ContextUtils.deriveParentTaskContextId("1,2"));
        assertEquals("1,2", ContextUtils.deriveParentTaskContextId("1,2,3"));
        assertEquals("1,2,3", ContextUtils.deriveParentTaskContextId("1,2,3,4"));
    }

    // === deriveParentTaskContextId - first-level fan-out (no |) ===

    @Test
    public void test_deriveParent_firstLevelFanOut() {
        assertEquals("1", ContextUtils.deriveParentTaskContextId("1,2#1"));
        assertEquals("1", ContextUtils.deriveParentTaskContextId("1,2#8"));
    }

    // === deriveParentTaskContextId - second-level (single ancestor) ===

    @Test
    public void test_deriveParent_secondLevel() {
        // "1,2,5|1#0" -> parent = "1,2#1"
        assertEquals("1,2#1", ContextUtils.deriveParentTaskContextId("1,2,5|1#0"));
        assertEquals("1,2#1", ContextUtils.deriveParentTaskContextId("1,2,5|1#1"));
        assertEquals("1,2#1", ContextUtils.deriveParentTaskContextId("1,2,5|1#2"));
        assertEquals("1,2#2", ContextUtils.deriveParentTaskContextId("1,2,5|2#0"));
        assertEquals("1,2#3", ContextUtils.deriveParentTaskContextId("1,2,3|3#0"));
    }

    // === deriveParentTaskContextId - third-level (two ancestors) ===

    @Test
    public void test_deriveParent_thirdLevel() {
        // "1,2,5,6|1|0#0" -> parent = "1,2,5|1#0"
        assertEquals("1,2,5|1#0", ContextUtils.deriveParentTaskContextId("1,2,5,6|1|0#0"));
        // "1,2,5,6|1|2#0" -> parent = "1,2,5|1#2"
        assertEquals("1,2,5|1#2", ContextUtils.deriveParentTaskContextId("1,2,5,6|1|2#0"));
        // "1,2,5,6|2|0#0" -> parent = "1,2,5|2#0"
        assertEquals("1,2,5|2#0", ContextUtils.deriveParentTaskContextId("1,2,5,6|2|0#0"));
    }

    // === deriveParentTaskContextId - fourth-level (three ancestors) ===

    @Test
    public void test_deriveParent_fourthLevel() {
        // "1,2,5,6,7|1|0|2#0" -> parent = "1,2,5,6|1|0#2"
        assertEquals("1,2,5,6|1|0#2", ContextUtils.deriveParentTaskContextId("1,2,5,6,7|1|0|2#0"));
    }

    // === Full chain walk: getCurrTaskContextIdForSubProcesses -> buildTaskContextId -> deriveParent roundtrip ===

    @Test
    public void test_fullRoundtrip_threeLevel() {
        // Level 0: top-level context "1"
        // Level 1: batch-splitter creates "1,2#1"
        String level1ctx = ContextUtils.buildTaskContextId("1,2", "1");
        assertEquals("1,2#1", level1ctx);
        assertEquals("1", ContextUtils.deriveParentTaskContextId(level1ctx));

        // Level 2: entering subprocess 1,2,5 from "1,2#1"
        String level2base = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5");
        assertEquals("1,2,5|1", level2base);
        // batch-splitter creates instance 0
        String level2ctx = ContextUtils.buildTaskContextId(level2base, "0");
        assertEquals("1,2,5|1#0", level2ctx);
        assertEquals("1,2#1", ContextUtils.deriveParentTaskContextId(level2ctx));

        // Level 3: entering subprocess 1,2,5,6 from "1,2,5|1#0"
        String level3base = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6");
        assertEquals("1,2,5,6|1|0", level3base);
        String level3ctx = ContextUtils.buildTaskContextId(level3base, "0");
        assertEquals("1,2,5,6|1|0#0", level3ctx);
        assertEquals("1,2,5|1#0", ContextUtils.deriveParentTaskContextId(level3ctx));
    }

    @Test
    public void test_fullRoundtrip_fourLevel() {
        // Continue from level 3: "1,2,5,6|1|0#2" enters subprocess 1,2,5,6,7
        String level4base = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5,6|1|0#2", "1,2,5,6,7");
        assertEquals("1,2,5,6,7|1|0|2", level4base);
        String level4ctx = ContextUtils.buildTaskContextId(level4base, "0");
        assertEquals("1,2,5,6,7|1|0|2#0", level4ctx);
        assertEquals("1,2,5,6|1|0#2", ContextUtils.deriveParentTaskContextId(level4ctx));
    }

    // === Variable scoping chain walk ===

    @Test
    public void test_variableLookup_secondLevel_isolation() {
        String ctx = "1,2,3|1#0";
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        String curr = ctx;
        while (curr != null) {
            visited.add(curr);
            curr = VariableUtils.getParentContext(curr);
        }
        assertEquals(java.util.List.of("1,2,3|1#0", "1,2#1", "1"), new java.util.ArrayList<>(visited));
        assertFalse(visited.contains("1,2#2"));
        assertFalse(visited.contains("1,2#3"));
    }

    @Test
    public void test_variableLookup_thirdLevel_fullChain() {
        // "1,2,5,6|1|0#0" -> "1,2,5|1#0" -> "1,2#1" -> "1" -> null
        String ctx = "1,2,5,6|1|0#0";
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        String curr = ctx;
        while (curr != null) {
            visited.add(curr);
            curr = VariableUtils.getParentContext(curr);
        }
        assertEquals(
                java.util.List.of("1,2,5,6|1|0#0", "1,2,5|1#0", "1,2#1", "1"),
                new java.util.ArrayList<>(visited));
    }

    @Test
    public void test_variableLookup_fourthLevel_fullChain() {
        // "1,2,5,6,7|1|0|2#0" -> "1,2,5,6|1|0#2" -> "1,2,5|1#0" -> "1,2#1" -> "1" -> null
        String ctx = "1,2,5,6,7|1|0|2#0";
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        String curr = ctx;
        while (curr != null) {
            visited.add(curr);
            curr = VariableUtils.getParentContext(curr);
        }
        assertEquals(
                java.util.List.of("1,2,5,6,7|1|0|2#0", "1,2,5,6|1|0#2", "1,2,5|1#0", "1,2#1", "1"),
                new java.util.ArrayList<>(visited));
    }

    // === buildHierarchicalContextOrder - deep nesting ===

    @Test
    public void test_buildHierarchicalContextOrder_deepNesting() {
        java.util.Set<String> contexts = new java.util.LinkedHashSet<>(java.util.List.of(
                "1",
                "1,2#0", "1,2,3|0#0", "1,2,5|0#0", "1,2,5,6|0|0#0",
                "1,2#1", "1,2,3|1#0", "1,2,5|1#0", "1,2,5,6|1|0#0", "1,2,5|1#1", "1,2,5|1#2",
                "1,2#2", "1,2,3|2#0"
        ));

        java.util.List<String> result = ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtils.buildHierarchicalContextOrder(contexts);

        int idx_1_2_hash1 = result.indexOf("1,2#1");
        int idx_1_2_5_p1_hash0 = result.indexOf("1,2,5|1#0");
        int idx_deep = result.indexOf("1,2,5,6|1|0#0");
        int idx_1_2_5_p1_hash2 = result.indexOf("1,2,5|1#2");
        int idx_1_2_hash2 = result.indexOf("1,2#2");

        assertTrue(idx_1_2_hash1 < idx_1_2_5_p1_hash0,
                "1,2#1 before 1,2,5|1#0. Order: " + result);
        assertTrue(idx_1_2_5_p1_hash0 < idx_deep,
                "1,2,5|1#0 before 1,2,5,6|1|0#0. Order: " + result);
        assertTrue(idx_deep < idx_1_2_hash2,
                "1,2,5,6|1|0#0 before 1,2#2. Order: " + result);
        assertTrue(idx_1_2_5_p1_hash2 < idx_1_2_hash2,
                "1,2,5|1#2 before 1,2#2. Order: " + result);
    }
}
