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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT for InternalFunctionService.groupBodySubProcesses.
 *
 * Repro of the ExecContext #31 / Task #1357 failure:
 *   977.060 system error while processing internal function 'mh.nop', error:
 *   995.300 An error while saving data to file,
 *   375.100 Different contextId, prev: 1,2,3,4, next: 1,2,3,6
 */
@Execution(ExecutionMode.CONCURRENT)
public class InternalFunctionServiceGroupBodyTest {

    private static ExecContextParamsYaml.Process proc(String processCode, String internalContextId) {
        ExecContextParamsYaml.Process p = new ExecContextParamsYaml.Process();
        p.processCode = processCode;
        p.processName = processCode;
        p.internalContextId = internalContextId;
        return p;
    }

    /**
     * A grafted group body holds two SEQUENTIAL siblings at the SAME internalContextId "2,3"
     * (that is how the .mhsc compiler lays sequential siblings - one shared sub-context). Each
     * sibling has its own children, so EVERY child shares the "2,3," prefix. The body list is
     * kept in DFS pre-order.
     */
    private static ExecContextParamsYaml paramsWithSiblingCollision() {
        ExecContextParamsYaml py = new ExecContextParamsYaml();
        ExecContextParamsYaml.Group g = new ExecContextParamsYaml.Group("req-rung");
        g.internalContextId = "2";
        // DFS pre-order body:
        g.body.add(proc("w-seq",    "2,3"));    // running wrapper (sequential sibling)
        g.body.add(proc("w-child",  "2,3,4"));  // w-seq's ONLY real direct child
        g.body.add(proc("s-par",    "2,3"));    // sequential SIBLING of w-seq (same ctx "2,3")
        g.body.add(proc("s-child1", "2,3,5"));  // s-par's child (belongs to s-par, NOT w-seq)
        g.body.add(proc("s-child2", "2,3,6"));  // s-par's child (belongs to s-par, NOT w-seq)
        py.groups.add(g);
        return py;
    }

    @Test
    public void test_groupBodySubProcesses_sequentialSiblingCollision() {
        ExecContextParamsYaml py = paramsWithSiblingCollision();
        ExecContextParamsYaml.Process wSeq = py.groups.get(0).body.get(0);

        List<ExecContextApiData.ProcessVertex> result =
                InternalFunctionService.groupBodySubProcesses(py, wSeq, "1,2,3");

        List<String> ctxIds = result.stream().map(v -> v.processContextId).toList();

        // Correct behavior: "w-seq" owns exactly ONE direct child (w-child -> 1,2,3,4). The
        // sibling "s-par"'s children (1,2,3,5 / 1,2,3,6) must NOT be attributed to w-seq, so the
        // sequential check downstream sees a single consistent contextId instead of a mismatch.
        assertEquals(1, result.size(), () -> "ctxIds=" + ctxIds);
        assertEquals("1,2,3,4", ctxIds.get(0));
        assertFalse(ctxIds.contains("1,2,3,5"));
        assertFalse(ctxIds.contains("1,2,3,6"));
    }

    /**
     * Guard for the legitimate PARALLEL parent: a parallel wrapper's children DO carry distinct
     * one-segment contexts by design, and all of them must be returned. Subtree scoping must not
     * over-trim (both branches kept), must skip grandchildren (not direct), and must exclude a
     * preceding sequential SIBLING's child.
     */
    private static ExecContextParamsYaml paramsWithParallelParent() {
        ExecContextParamsYaml py = new ExecContextParamsYaml();
        ExecContextParamsYaml.Group g = new ExecContextParamsYaml.Group("req-rung");
        g.internalContextId = "2";
        // DFS pre-order body:
        g.body.add(proc("wrapper-seq",  "2,7"));      // sequential sibling, appears BEFORE the parallel one
        g.body.add(proc("w-child",      "2,7,8"));    // wrapper-seq's child (must be excluded for amend-par)
        g.body.add(proc("amend-par",    "2,7"));      // PARALLEL parent (sibling of wrapper-seq, same ctx)
        g.body.add(proc("active",       "2,7,9"));    // amend-par's direct child
        g.body.add(proc("active-inner", "2,7,9,11")); // grandchild (NOT a direct child)
        g.body.add(proc("obsolete",     "2,7,10"));   // amend-par's direct child
        g.body.add(proc("obsolete-in",  "2,7,10,12"));// grandchild (NOT a direct child)
        py.groups.add(g);
        return py;
    }

    @Test
    public void test_groupBodySubProcesses_parallelParentKeepsBothBranches() {
        ExecContextParamsYaml py = paramsWithParallelParent();
        ExecContextParamsYaml.Process amendPar = py.groups.get(0).body.get(2);

        List<ExecContextApiData.ProcessVertex> result =
                InternalFunctionService.groupBodySubProcesses(py, amendPar, "1,2,3");

        List<String> ctxIds = result.stream().map(v -> v.processContextId).toList();

        assertEquals(2, result.size(), () -> "ctxIds=" + ctxIds);
        assertTrue(ctxIds.contains("1,2,3,9"));
        assertTrue(ctxIds.contains("1,2,3,10"));
        // grandchildren are not direct children
        assertFalse(ctxIds.contains("1,2,3,9,11"));
        assertFalse(ctxIds.contains("1,2,3,10,12"));
        // the preceding sequential sibling's child is not attributed to amend-par
        assertFalse(ctxIds.contains("1,2,3,8"));
    }
}
