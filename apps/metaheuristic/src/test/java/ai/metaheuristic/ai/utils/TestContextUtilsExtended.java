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
import ai.metaheuristic.commons.utils.ContextUtils;

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
        assertEquals("1,2#0", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2", "0"));
    }

    @Test
    public void test_buildTaskContextId_multipleInstances() {
        assertEquals("1,2#1", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2", "1"));
        assertEquals("1,2#2", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2", "2"));
        assertEquals("1,2#100", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2", "100"));
    }

    @Test
    public void test_buildTaskContextId_withAncestors() {
        assertEquals("1,2,5|1#0", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2,5|1", "0"));
        assertEquals("1,2,5,6|1|0#0", ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2,5,6|1|0", "0"));
    }

    // === getCurrTaskContextIdForSubProcesses ===

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_topLevel() {
        assertEquals("1,2", ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1", "1,2"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_firstLevel() {
        // "1,2#1" -> path="1", no ancestors -> "1,2,5|1"
        assertEquals("1,2,5|1", ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_secondLevel() {
        // "1,2,5|1#0" -> path="0", ancestors="1" -> new ancestors="1|0"
        assertEquals("1,2,5,6|1|0", ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_thirdLevel() {
        // "1,2,5,6|1|0#2" -> path="2", ancestors="1|0" -> new ancestors="1|0|2"
        assertEquals("1,2,5,6,7|1|0|2", ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5,6|1|0#2", "1,2,5,6,7"));
    }

    @Test
    public void test_getCurrTaskContextIdForSubProcesses_noHash() {
        assertEquals("1,2,3", ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2", "1,2,3"));
    }

    // === getProcessContextId and getAncestorPath ===

    @Test
    public void test_getProcessContextId() {
        assertEquals("1,2", ai.metaheuristic.commons.utils.ContextUtils.getProcessContextId("1,2"));
        assertEquals("1,2,5", ai.metaheuristic.commons.utils.ContextUtils.getProcessContextId("1,2,5|1"));
        assertEquals("1,2,5,6", ai.metaheuristic.commons.utils.ContextUtils.getProcessContextId("1,2,5,6|1|0"));
    }

    @Test
    public void test_getAncestorPath() {
        assertNull(ai.metaheuristic.commons.utils.ContextUtils.getAncestorPath("1,2"));
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.getAncestorPath("1,2,5|1"));
        assertEquals("1|0", ai.metaheuristic.commons.utils.ContextUtils.getAncestorPath("1,2,5,6|1|0"));
        assertEquals("1|0|2", ai.metaheuristic.commons.utils.ContextUtils.getAncestorPath("1,2,5,6,7|1|0|2"));
    }

    // === getLevel and getPath ===

    @Test
    public void test_getLevel() {
        assertEquals("1,2", ai.metaheuristic.commons.utils.ContextUtils.getLevel("1,2#3"));
        assertEquals("1,2,5|1", ai.metaheuristic.commons.utils.ContextUtils.getLevel("1,2,5|1#0"));
        assertEquals("1,2,5,6|1|0", ai.metaheuristic.commons.utils.ContextUtils.getLevel("1,2,5,6|1|0#0"));
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.getLevel("1"));
    }

    @Test
    public void test_getPath() {
        assertEquals("3", ai.metaheuristic.commons.utils.ContextUtils.getPath("1,2#3"));
        assertEquals("0", ai.metaheuristic.commons.utils.ContextUtils.getPath("1,2,5|1#0"));
        assertNull(ai.metaheuristic.commons.utils.ContextUtils.getPath("1"));
    }

    // === deriveParentTaskContextId - root ===

    @Test
    public void test_deriveParent_root() {
        assertNull(ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1"));
    }

    // === deriveParentTaskContextId - simple nested (no #) ===

    @Test
    public void test_deriveParent_simpleNested() {
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2"));
        assertEquals("1,2", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,3"));
        assertEquals("1,2,3", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,3,4"));
    }

    // === deriveParentTaskContextId - first-level fan-out (no |) ===

    @Test
    public void test_deriveParent_firstLevelFanOut() {
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2#1"));
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2#8"));
    }

    // === deriveParentTaskContextId - second-level (single ancestor) ===

    @Test
    public void test_deriveParent_secondLevel() {
        // "1,2,5|1#0" -> parent = "1,2#1"
        assertEquals("1,2#1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5|1#0"));
        assertEquals("1,2#1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5|1#1"));
        assertEquals("1,2#1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5|1#2"));
        assertEquals("1,2#2", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5|2#0"));
        assertEquals("1,2#3", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,3|3#0"));
    }

    // === deriveParentTaskContextId - third-level (two ancestors) ===

    @Test
    public void test_deriveParent_thirdLevel() {
        // "1,2,5,6|1|0#0" -> parent = "1,2,5|1#0"
        assertEquals("1,2,5|1#0", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5,6|1|0#0"));
        // "1,2,5,6|1|2#0" -> parent = "1,2,5|1#2"
        assertEquals("1,2,5|1#2", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5,6|1|2#0"));
        // "1,2,5,6|2|0#0" -> parent = "1,2,5|2#0"
        assertEquals("1,2,5|2#0", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5,6|2|0#0"));
    }

    // === deriveParentTaskContextId - fourth-level (three ancestors) ===

    @Test
    public void test_deriveParent_fourthLevel() {
        // "1,2,5,6,7|1|0|2#0" -> parent = "1,2,5,6|1|0#2"
        assertEquals("1,2,5,6|1|0#2", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId("1,2,5,6,7|1|0|2#0"));
    }

    // === Full chain walk: getCurrTaskContextIdForSubProcesses -> buildTaskContextId -> deriveParent roundtrip ===

    @Test
    public void test_fullRoundtrip_threeLevel() {
        // Level 0: top-level context "1"
        // Level 1: batch-splitter creates "1,2#1"
        String level1ctx = ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId("1,2", "1");
        assertEquals("1,2#1", level1ctx);
        assertEquals("1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId(level1ctx));

        // Level 2: entering subprocess 1,2,5 from "1,2#1"
        String level2base = ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5");
        assertEquals("1,2,5|1", level2base);
        // batch-splitter creates instance 0
        String level2ctx = ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId(level2base, "0");
        assertEquals("1,2,5|1#0", level2ctx);
        assertEquals("1,2#1", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId(level2ctx));

        // Level 3: entering subprocess 1,2,5,6 from "1,2,5|1#0"
        String level3base = ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6");
        assertEquals("1,2,5,6|1|0", level3base);
        String level3ctx = ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId(level3base, "0");
        assertEquals("1,2,5,6|1|0#0", level3ctx);
        assertEquals("1,2,5|1#0", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId(level3ctx));
    }

    @Test
    public void test_fullRoundtrip_fourLevel() {
        // Continue from level 3: "1,2,5,6|1|0#2" enters subprocess 1,2,5,6,7
        String level4base = ai.metaheuristic.commons.utils.ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5,6|1|0#2", "1,2,5,6,7");
        assertEquals("1,2,5,6,7|1|0|2", level4base);
        String level4ctx = ai.metaheuristic.commons.utils.ContextUtils.buildTaskContextId(level4base, "0");
        assertEquals("1,2,5,6,7|1|0|2#0", level4ctx);
        assertEquals("1,2,5,6|1|0#2", ai.metaheuristic.commons.utils.ContextUtils.deriveParentTaskContextId(level4ctx));
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

    // =========================================================================
    // Phase 0 residual verification (025-MHSC-DSL-V2-PLAN) - graft rebase.
    //
    // Characterization (NOT a bug fix): the UNMODIFIED ContextUtils already
    // supports the DSL-v2 graft, so these are green with zero production change.
    // attachGroup REBASES a body's processContextId onto the target
    //   target.processContextId + "," + <freshSlot> + <body suffix>
    // and instantiates via the SAME descent primitive the splitter uses
    // (getCurrTaskContextIdForSubProcesses). Asserted:
    //   (a) every grafted task derives its parent up to the target and resolves
    //       its own inputs (parent-chain walk == variable-resolution walk, i.e.
    //       VariableUtils.getParentContext -> deriveParentTaskContextId): no
    //       orphan (171.520), no sibling-variable / 020 leak;
    //   (b) two sibling grafts do not collide - different groups (distinct
    //       freshSlot components) and repeats of the same group (fresh instance
    //       numbers via nextSiblingTaskContextId).
    // Body group namespace (own, reusable): root "1", inner splitter "1,2",
    // splitter child template "1,2,3". Target: a splitter child "1,2#5".
    // =========================================================================

    /** Rebase a body process onto the target: substitute the body-root prefix with
     *  target.processContextId + "," + freshSlot, keeping any nested suffix. */
    private static String rebase(String targetProcessContextId, String freshSlot,
                                 String bodyRootProcessContextId, String bodyProcessContextId) {
        String rebasedRoot = targetProcessContextId + "," + freshSlot;
        if (bodyProcessContextId.equals(bodyRootProcessContextId)) {
            return rebasedRoot;
        }
        String suffix = bodyProcessContextId.substring(bodyRootProcessContextId.length());
        return rebasedRoot + suffix;
    }

    /** Walk the parent chain the way the variable engine resolves inputs
     *  (VariableUtils.getParentContext delegates to deriveParentTaskContextId). */
    private static java.util.List<String> parentChain(String taskContextId) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        String curr = taskContextId;
        while (curr != null) {
            chain.add(curr);
            curr = ContextUtils.deriveParentTaskContextId(curr);
        }
        return chain;
    }

    @Test
    public void test_graftRebase_rootTaskDerivesParentUpToTarget() {
        String target = "1,2#5";
        String targetPcid = ContextUtils.getProcessContextId(ContextUtils.getLevel(target));
        assertEquals("1,2", targetPcid);

        String rebasedRoot = rebase(targetPcid, "9", "1", "1");
        assertEquals("1,2,9", rebasedRoot);

        // instantiate via the same primitive the splitter uses
        String rootLevel = ContextUtils.getCurrTaskContextIdForSubProcesses(target, rebasedRoot);
        assertEquals("1,2,9|5", rootLevel);

        String rootTask = ContextUtils.buildTaskContextId(rootLevel, "0");
        assertEquals("1,2,9|5#0", rootTask);

        // the grafted root task walks its parent straight to the target
        assertEquals(target, ContextUtils.deriveParentTaskContextId(rootTask));
    }

    @Test
    public void test_graftRebase_innerSplitterChildDerivesFullChainToTarget() {
        String target = "1,2#5";
        String rootTask = "1,2,9|5#0";

        // inner splitter process rebased: body "1,2" -> "1,2,9,2"
        String rebasedSplitter = rebase("1,2", "9", "1", "1,2");
        assertEquals("1,2,9,2", rebasedSplitter);

        // splitter descent from the grafted root task (nested dynamic subprocess)
        String splitterBase = ContextUtils.getCurrTaskContextIdForSubProcesses(rootTask, rebasedSplitter);
        assertEquals("1,2,9,2|5|0", splitterBase);

        String child0 = ContextUtils.buildTaskContextId(splitterBase, "0");
        assertEquals("1,2,9,2|5|0#0", child0);

        // the resolution walk reaches the target and terminates at the root - no orphan, no leak
        java.util.List<String> chain = parentChain(child0);
        assertEquals(java.util.List.of("1,2,9,2|5|0#0", "1,2,9|5#0", "1,2#5", "1"), chain);
        assertTrue(chain.contains(target));
    }

    @Test
    public void test_graftRebase_differentGroupsSiblingGraftsDoNotCollide() {
        String target = "1,2#5";

        // different groups at one target => distinct freshSlot components (9 vs 10)
        String aRoot = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(target, rebase("1,2", "9", "1", "1")), "0");
        String bRoot = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(target, rebase("1,2", "10", "1", "1")), "0");
        assertEquals("1,2,9|5#0", aRoot);
        assertEquals("1,2,10|5#0", bRoot);
        assertNotEquals(aRoot, bRoot);

        String aChild = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(aRoot, rebase("1,2", "9", "1", "1,2")), "0");
        String bChild = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(bRoot, rebase("1,2", "10", "1", "1,2")), "0");
        assertEquals("1,2,9,2|5|0#0", aChild);
        assertEquals("1,2,10,2|5|0#0", bChild);
        assertNotEquals(aChild, bChild);

        // no 020 leak: graft A's resolution chain never visits any of graft B's contexts
        java.util.List<String> aChainChild = parentChain(aChild);
        assertFalse(parentChain(aRoot).contains(bRoot));
        assertFalse(aChainChild.contains(bRoot));
        assertFalse(aChainChild.contains(bChild));
        // both grafts still derive up to the shared target
        assertTrue(aChainChild.contains(target));
        assertTrue(parentChain(bChild).contains(target));
    }

    @Test
    public void test_graftRebase_sameGroupRepeatsDoNotCollide() {
        String target = "1,2#5";
        // same group => same freshSlot (9) => graft-root LEVEL identical; instances disambiguate
        String rootLevel = ContextUtils.getCurrTaskContextIdForSubProcesses(target, rebase("1,2", "9", "1", "1"));
        assertEquals("1,2,9|5", rootLevel);

        String first = ContextUtils.nextSiblingTaskContextId(rootLevel, java.util.List.of());
        assertEquals("1,2,9|5#1", first);
        String second = ContextUtils.nextSiblingTaskContextId(rootLevel, java.util.List.of(first));
        assertEquals("1,2,9|5#2", second);
        assertNotEquals(first, second);

        // parent is level-determined (instance-independent): both repeats reach the SAME target
        assertEquals(target, ContextUtils.deriveParentTaskContextId(first));
        assertEquals(target, ContextUtils.deriveParentTaskContextId(second));

        // inner subtrees separated by the ancestor-instance segment - no collision
        String firstChild = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(first, rebase("1,2", "9", "1", "1,2")), "0");
        String secondChild = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(second, rebase("1,2", "9", "1", "1,2")), "0");
        assertEquals("1,2,9,2|5|1#0", firstChild);
        assertEquals("1,2,9,2|5|2#0", secondChild);
        assertNotEquals(firstChild, secondChild);
        assertEquals(first, ContextUtils.deriveParentTaskContextId(firstChild));
        assertEquals(second, ContextUtils.deriveParentTaskContextId(secondChild));
    }

    @Test
    public void test_graftRebase_unRebasedBodyRootOrphans_documentsRejectedAlternative() {
        String target = "1,2#5";
        // REJECTED path: keep the body root at its own single-component processContextId "1"
        String orphanLevel = ContextUtils.getCurrTaskContextIdForSubProcesses(target, "1");
        assertEquals("1|5", orphanLevel);
        String orphanTask = ContextUtils.buildTaskContextId(orphanLevel, "0");
        assertEquals("1|5#0", orphanTask);
        // getProcessContextId has no comma => parent derivation returns null => the body orphans
        assertEquals("1", ContextUtils.getProcessContextId(orphanLevel));
        assertNull(ContextUtils.deriveParentTaskContextId(orphanTask));

        // the rebased root, by contrast, derives correctly up to the target
        String rebasedRootTask = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(target, rebase("1,2", "9", "1", "1")), "0");
        assertEquals(target, ContextUtils.deriveParentTaskContextId(rebasedRootTask));
    }
}
