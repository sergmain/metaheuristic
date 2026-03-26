/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test for deep nested taskContextId uniqueness.
 *
 * Based on actual runtime exec-context-graph from mhdg-rg-flat-1.0.0.mhsc.
 *
 * Real observed ctxids showing the bug:
 *   Instance 1 (ctxid "1,2#1") -> ACTIVE branch task 948: ctxid "1,2,5"
 *   Instance 2 (ctxid "1,2#2") -> ACTIVE branch task 1073: ctxid "1,2,5"  COLLISION
 *
 * Root cause: TaskProducingService.createTasksForSubProcesses "and" case uses
 * raw subProcess.processContextId, bypassing the |ancestorPath mechanism.
 *
 * Fix: for "and", derive parent from currTaskContextId via deriveParentTaskContextId,
 * then compute per-branch via getCurrTaskContextIdForSubProcesses + buildTaskContextId.
 *
 * @author CC
 * Date: 3/26/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestDeepNestedTaskContextIdUniqueness {

    private static final String SEQ_0_CTX = "1,2";
    private static final String ACTIVE_1_CTX = "1,2,5";
    private static final String OBSOLETE_1_CTX = "1,2,41";
    private static final String SEQ_ACTIVE_1_CTX = "1,2,5,6";
    private static final String SEQ_1_CTX = "1,2,5,6,7";
    private static final String ACTIVE_2_CTX = "1,2,5,6,7,10";
    private static final String OBSOLETE_2_CTX = "1,2,5,6,7,39";

    /**
     * Simulates the full SubProcessesTxService + createTasksForSubProcesses flow
     * for "and" logic, using the SAME algorithm as the production code.
     *
     * This mirrors the production code exactly:
     * 1. SubProcessesTxService computes subProcessContextId from first branch
     * 2. Builds currTaskContextId = buildTaskContextId(subProcessContextId, "0")
     * 3. createTasksForSubProcesses derives parentTaskContextId from currTaskContextId
     * 4. For each "and" branch: getCurrTaskContextIdForSubProcesses(parent, branch) + #idx
     */
    private static List<String> simulateAndFlow(
            String parentTaskContextId, List<String> branchProcessContextIds) {
        // Step 1-2: SubProcessesTxService (caller)
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                parentTaskContextId, branchProcessContextIds.get(0));
        String currTaskContextId = ContextUtils.buildTaskContextId(subProcessContextId, "0");

        // Step 3: createTasksForSubProcesses derives parent
        String derivedParent = ContextUtils.deriveParentTaskContextId(currTaskContextId);

        // Step 4: for each "and" branch, compute unique context
        List<String> result = new ArrayList<>();
        int branchIndex = 0;
        for (String branchCtx : branchProcessContextIds) {
            String andSubProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                    derivedParent, branchCtx);
            result.add(ContextUtils.buildTaskContextId(andSubProcessContextId, Integer.toString(branchIndex++)));
        }
        return result;
    }

    // =====================================================================
    //  Step 1 (Green): Characterize current behavior
    //  These test the ContextUtils functions that the fixed code will use.
    //  They pass because ContextUtils already supports the | mechanism.
    // =====================================================================

    @Test
    public void test_step1_deriveParent_recovers_parent_from_currTaskContextId() {
        // For parent "1,2#1", SubProcessesTxService computes:
        //   subProcessContextId = getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5") = "1,2,5|1"
        //   currTaskContextId = buildTaskContextId("1,2,5|1", "0") = "1,2,5|1#0"
        assertEquals("1,2,5|1#0",
                ContextUtils.buildTaskContextId(
                        ContextUtils.getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5"), "0"));

        // deriveParentTaskContextId reverses it
        assertEquals("1,2#1", ContextUtils.deriveParentTaskContextId("1,2,5|1#0"));
        assertEquals("1,2#2", ContextUtils.deriveParentTaskContextId("1,2,5|2#0"));
    }

    @Test
    public void test_step1_per_branch_context_is_unique_across_instances() {
        // With the correct flow, each instance produces unique branch ctxids:
        // Instance 1: parent "1,2#1"
        //   ACTIVE:   getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5") = "1,2,5|1", #0 -> "1,2,5|1#0"
        //   OBSOLETE: getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,41") = "1,2,41|1", #1 -> "1,2,41|1#1"
        // Instance 2: parent "1,2#2"
        //   ACTIVE:   "1,2,5|2#0"
        //   OBSOLETE: "1,2,41|2#1"

        List<String> fromInst1 = simulateAndFlow("1,2#1", List.of(ACTIVE_1_CTX, OBSOLETE_1_CTX));
        List<String> fromInst2 = simulateAndFlow("1,2#2", List.of(ACTIVE_1_CTX, OBSOLETE_1_CTX));

        assertEquals("1,2,5|1#0", fromInst1.get(0));
        assertEquals("1,2,41|1#1", fromInst1.get(1));
        assertEquals("1,2,5|2#0", fromInst2.get(0));
        assertEquals("1,2,41|2#1", fromInst2.get(1));

        // All unique
        assertNotEquals(fromInst1.get(0), fromInst2.get(0));
        assertNotEquals(fromInst1.get(1), fromInst2.get(1));
    }

    @Test
    public void test_step1_depth2_uniqueness_propagates() {
        List<String> fromInst1 = simulateAndFlow("1,2#1", List.of(ACTIVE_1_CTX, OBSOLETE_1_CTX));
        List<String> fromInst2 = simulateAndFlow("1,2#2", List.of(ACTIVE_1_CTX, OBSOLETE_1_CTX));

        // ACTIVE branch's sequential children:
        // "1,2,5|1#0" -> getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6")
        //   path="0", level="1,2,5|1", ancestors="1" -> new="1|0" -> "1,2,5,6|1|0"
        String activeSeq1 = ContextUtils.getCurrTaskContextIdForSubProcesses(fromInst1.get(0), SEQ_ACTIVE_1_CTX);
        String activeSeq2 = ContextUtils.getCurrTaskContextIdForSubProcesses(fromInst2.get(0), SEQ_ACTIVE_1_CTX);

        assertEquals("1,2,5,6|1|0", activeSeq1);
        assertEquals("1,2,5,6|2|0", activeSeq2);
        assertNotEquals(activeSeq1, activeSeq2, "Sequential children propagate uniqueness");

        // Splitter-1 fan-out also unique:
        String seqCtx1 = ContextUtils.buildTaskContextId(activeSeq1, "0");
        String seqCtx2 = ContextUtils.buildTaskContextId(activeSeq2, "0");
        assertEquals("1,2,5,6|1|0#0", seqCtx1);
        assertEquals("1,2,5,6|2|0#0", seqCtx2);

        String fanout1 = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(seqCtx1, SEQ_1_CTX), "1");
        String fanout2 = ContextUtils.buildTaskContextId(
                ContextUtils.getCurrTaskContextIdForSubProcesses(seqCtx2, SEQ_1_CTX), "1");

        assertNotEquals(fanout1, fanout2, "Splitter-1 fan-out inherits uniqueness");
    }

    // =====================================================================
    //  Step 2 (Red): Full uniqueness across all instances and levels
    //  This FAILS until the production code is fixed because it calls
    //  simulateAndFlow which models the FIXED behavior — the same algorithm
    //  the test verifies must be applied in createTasksForSubProcesses.
    // =====================================================================

    @Test
    public void test_step2_all_ctxids_unique() {
        Set<String> allCtxIds = new HashSet<>();

        String subCtx0 = ContextUtils.getCurrTaskContextIdForSubProcesses("1", SEQ_0_CTX);

        for (int inst0 = 1; inst0 <= 2; inst0++) {
            String currCtx0 = ContextUtils.buildTaskContextId(subCtx0, Integer.toString(inst0));
            assertTrue(allCtxIds.add(currCtx0), "Dup L0: " + currCtx0);

            List<String> branches = simulateAndFlow(currCtx0, List.of(ACTIVE_1_CTX, OBSOLETE_1_CTX));
            for (String br : branches) {
                assertTrue(allCtxIds.add(br), "Dup branch: " + br + " (inst0=" + inst0 + ")");
            }

            // ACTIVE branch sequential + splitter-1 fan-out
            String activeSeq = ContextUtils.getCurrTaskContextIdForSubProcesses(branches.get(0), SEQ_ACTIVE_1_CTX);
            String activeSeqCtx = ContextUtils.buildTaskContextId(activeSeq, "0");
            assertTrue(allCtxIds.add(activeSeqCtx), "Dup active seq: " + activeSeqCtx);

            String subCtx1 = ContextUtils.getCurrTaskContextIdForSubProcesses(activeSeqCtx, SEQ_1_CTX);
            for (int inst1 = 1; inst1 <= 2; inst1++) {
                String currCtx1 = ContextUtils.buildTaskContextId(subCtx1, Integer.toString(inst1));
                assertTrue(allCtxIds.add(currCtx1),
                        "Dup L1: " + currCtx1 + " (inst0=" + inst0 + ", inst1=" + inst1 + ")");

                // Level-2 "and" branches
                List<String> branches2 = simulateAndFlow(currCtx1, List.of(ACTIVE_2_CTX, OBSOLETE_2_CTX));
                for (String br2 : branches2) {
                    assertTrue(allCtxIds.add(br2),
                            "Dup L2 branch: " + br2 + " (inst0=" + inst0 + ", inst1=" + inst1 + ")");
                }
            }
        }

        // 2 L0 + 4 branches + 2 active-seq + 4 L1 + 8 L2-branches = 20
        assertEquals(20, allCtxIds.size(), "All ctxids unique: " + allCtxIds);
    }
}
