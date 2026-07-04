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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Phase-1 STRUCTURAL acceptance for the flat / PLACE_NOW graft (025-MHSC-DSL-V2-PLAN, Phase 1).
 *
 * <p>Spring-less, house-style for the exec_context_graph package (cf. ExecContextGraphDuplicateBranchTest):
 * it drives the REAL {@link ExecContextGraftTxService#filterTerminalDescendants} static and the REAL
 * {@link ContextUtils} algebra the graft uses, asserting the two novel/high-risk structural guarantees:
 * <ul>
 *   <li>LINE ISOLATION - a grafted line's tail wires ONLY into the shared downstream terminal, never
 *       into a sibling line's head (the 020-leak-safe property);</li>
 *   <li>REBASE - the grafted head derives its parent up to the target and every graft gets a fresh,
 *       distinct line ctx.</li>
 * </ul>
 *
 * <p>Scenario: a splitter TARGET at ctx {@code "1"} with body first sub-process processContextId
 * {@code "1,2"}; a shared terminal (mh.finish) at ctx {@code "1"}.
 *
 * <p>NOTE: the exec-state / write-once-output / event-free-dispatch guarantees of {@code attachGroup}
 * are behavioral and need the Spring dispatcher context + DB; they are covered by a separate MH
 * integration test (out of this Spring-less structural suite).
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class ExecContextGraftFlatPlacementTest {

    private static final Function<ExecContextData.TaskVertex, String> CTX = v -> v.taskContextId;

    private static ExecContextData.TaskVertex v(Long taskId, String taskContextId) {
        return new ExecContextData.TaskVertex(taskId, taskContextId);
    }

    // === LINE ISOLATION - the createGroupTasksTx descendant filter ===

    @Test
    public void test_filterTerminalDescendants_excludesSiblingLinesKeepsSharedTerminal() {
        // Grafting line B ("1,2#2") when line A ("1,2#1") already exists. The target's live direct
        // descendants are the shared terminal + every per-line head (each a direct child of the target).
        ExecContextData.TaskVertex terminal = v(90L, "1");      // mh.finish - the shared terminal
        ExecContextData.TaskVertex headA = v(10L, "1,2#1");     // graft A head (a sibling line)
        ExecContextData.TaskVertex headB = v(20L, "1,2#2");     // graft B's own head
        Set<ExecContextData.TaskVertex> descendants = new LinkedHashSet<>(Set.of(terminal, headA, headB));

        Set<ExecContextData.TaskVertex> kept =
                ExecContextGraftTxService.filterTerminalDescendants(descendants, "1,2#2", CTX);

        // Only the shared terminal survives - line B's tail wires to it and to nothing per-line.
        assertEquals(1, kept.size(), "only the shared terminal should remain; kept=" + ids(kept));
        assertTrue(kept.contains(terminal));
        assertFalse(kept.contains(headA), "sibling line A head must be excluded (no cross-line edge / 020 leak)");
        assertFalse(kept.contains(headB), "the graft's own line must be excluded");
    }

    @Test
    public void test_filterTerminalDescendants_noHashKeepsAll() {
        // Defensive branch: a line ctx with no '#' yields a null prefix -> keep everything.
        ExecContextData.TaskVertex terminal = v(90L, "1");
        ExecContextData.TaskVertex other = v(11L, "1,2#1");
        Set<ExecContextData.TaskVertex> descendants = new LinkedHashSet<>(Set.of(terminal, other));

        Set<ExecContextData.TaskVertex> kept =
                ExecContextGraftTxService.filterTerminalDescendants(descendants, "1,2", CTX);

        assertEquals(2, kept.size());
        assertTrue(kept.contains(terminal) && kept.contains(other));
    }

    @Test
    public void test_filterTerminalDescendants_nullVertexCtxIsKept() {
        // A descendant whose ctx cannot be resolved (null) is conservatively kept (treated as terminal).
        ExecContextData.TaskVertex unresolved = v(12L, "1,2#2"); // ctx exists but resolver returns null below
        Set<ExecContextData.TaskVertex> descendants = new LinkedHashSet<>(Set.of(unresolved));

        Set<ExecContextData.TaskVertex> kept =
                ExecContextGraftTxService.filterTerminalDescendants(descendants, "1,2#2", x -> null);

        assertEquals(1, kept.size());
        assertTrue(kept.contains(unresolved));
    }

    // === REBASE - head derives to the target; fresh distinct line ctx per graft ===

    @Test
    public void test_graftRebase_headDerivesToTarget_freshLineCtxPerGraft() {
        final String targetCtx = "1";           // splitter target ctx
        final String bodyRootProcessCtxId = "1,2";

        // Orchestrator computes: base = descent from target into body root, then a fresh sibling line ctx.
        String base = ContextUtils.getCurrTaskContextIdForSubProcesses(targetCtx, bodyRootProcessCtxId);
        assertEquals("1,2", base);

        // Graft A - existing ctx ids are just the target/terminal level "1".
        String lineCtxA = ContextUtils.nextSiblingTaskContextId(base, Set.of("1"));
        assertEquals("1,2#1", lineCtxA);
        assertEquals(targetCtx, ContextUtils.deriveParentTaskContextId(lineCtxA), "graft A head derives up to the target");

        // Graft B - now line A exists, so B gets the next sibling instance.
        String lineCtxB = ContextUtils.nextSiblingTaskContextId(base, Set.of("1", lineCtxA));
        assertEquals("1,2#2", lineCtxB);
        assertEquals(targetCtx, ContextUtils.deriveParentTaskContextId(lineCtxB), "graft B head derives up to the target");

        // Fresh, distinct line ctx per graft (no collision).
        assertNotEquals(lineCtxA, lineCtxB);
    }

    private static String ids(Set<ExecContextData.TaskVertex> vs) {
        return vs.stream().map(x -> x.taskId).toList().toString();
    }
}
