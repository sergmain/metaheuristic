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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParams;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParams;
import ai.metaheuristic.api.EnumsApi;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Produces the goldens of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.2) by running TODAY's whole-ExecContext DOT
 * implementation - the static algorithms of {@link ExecContextGraphService} - over a DOT shape:
 * <ul>
 *   <li>derived joins: a line's tail's edge to a non-head Task of another ctx, read off the graph;</li>
 *   <li>descendants: {@code findDescendantsBounded} with an always-true bound, i.e. everything reachable;</li>
 *   <li>readiness: simulated valid runs from a fixed seed - start a ready Task (NONE -> IN_PROGRESS), or finish one in
 *       progress as OK or, with probability 0.1, as ERROR followed by today's SKIPPED propagation - recording, before every
 *       action, the states and what {@code findAllForAssigning(graph, states, true)} hands out;</li>
 *   <li>SKIPPED closure: every ERROR of those runs, before and after {@code setStateForAllChildrenTasksStatic}.</li>
 * </ul>
 * Removed in Phase 21 together with the DOT code; the goldens it wrote stay.
 */
public final class SegmentGoldenGenerator {

    private SegmentGoldenGenerator() {
    }

    private static final double ERROR_PROBABILITY = 0.1;

    public static SegmentGolden.Golden generate(SegmentFixtureShapes.DotShape shape, int readyStates, long seed, boolean withDescendants) {
        final DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = ExecContextGraphService.importExecContextGraph(shape.dot());
        final ExecContextGraph ecg = new ExecContextGraph();
        final ExecContextGraphParams gp = new ExecContextGraphParams();
        gp.graph = shape.dot();
        ecg.updateParams(gp);

        final Map<Long, List<Long>> descendants = new TreeMap<>();
        if (withDescendants) {
            for (ExecContextData.TaskVertex v : graph.vertexSet()) {
                descendants.put(v.taskId, ExecContextGraphService.findDescendantsBounded(graph, v.taskId, x -> true).stream()
                        .map(x -> x.taskId).sorted().toList());
            }
        }

        final List<SegmentGolden.ReadyCase> readiness = new ArrayList<>();
        final List<SegmentGolden.SkipCase> skips = new ArrayList<>();
        final Random rnd = new Random(seed);
        final Map<Long, String> ctxOf = graph.vertexSet().stream().collect(Collectors.toMap(v -> v.taskId, v -> v.taskContextId));
        for (int run = 0; readiness.size() < readyStates && run < 1000; run++) {
            final ExecContextTaskStateParams st = new ExecContextTaskStateParams();
            final List<Long> inProgress = new ArrayList<>();
            for (int step = 0; readiness.size() < readyStates; step++) {
                final ExecContextTaskState ects = new ExecContextTaskState();
                ects.updateParams(st);
                final List<Long> ready = ExecContextGraphService.findAllForAssigning(ecg, ects, true).stream()
                        .map(v -> v.taskId).sorted().toList();
                readiness.add(new SegmentGolden.ReadyCase(run, step, copy(st.states), ready));
                if (ready.isEmpty() && inProgress.isEmpty()) {
                    break;
                }
                if (!inProgress.isEmpty() && (ready.isEmpty() || rnd.nextBoolean())) {
                    final long t = inProgress.remove(rnd.nextInt(inProgress.size()));
                    if (rnd.nextDouble() < ERROR_PROBABILITY) {
                        st.states.put(t, EnumsApi.TaskExecState.ERROR);
                        final Map<Long, EnumsApi.TaskExecState> before = copy(st.states);
                        ExecContextGraphService.setStateForAllChildrenTasksStatic(
                                new ExecContextData.ExecContextDAC(1L, graph, 0), st, t,
                                new ExecContextOperationStatusWithTaskList(), EnumsApi.TaskExecState.SKIPPED, ctxOf.get(t));
                        skips.add(new SegmentGolden.SkipCase(run, step, t, before, copy(st.states)));
                    }
                    else {
                        st.states.put(t, EnumsApi.TaskExecState.OK);
                    }
                }
                else {
                    final long t = ready.get(rnd.nextInt(ready.size()));
                    st.states.put(t, EnumsApi.TaskExecState.IN_PROGRESS);
                    inProgress.add(t);
                }
            }
        }
        return new SegmentGolden.Golden(shape.id(), derivedJoins(graph), descendants, readiness, skips);
    }

    private static Map<Long, EnumsApi.TaskExecState> copy(Map<Long, EnumsApi.TaskExecState> states) {
        final Map<Long, EnumsApi.TaskExecState> m = new TreeMap<>();
        states.forEach((k, v) -> {
            if (v != EnumsApi.TaskExecState.NONE) {
                m.put(k, v);
            }
        });
        return m;
    }

    /** For each ctx whose head has an incoming edge: the one out-edge of its tail into a non-head Task of another ctx. */
    private static Map<String, Long> derivedJoins(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> g) {
        final Map<String, List<ExecContextData.TaskVertex>> byCtx = g.vertexSet().stream()
                .collect(Collectors.groupingBy(v -> v.taskContextId));
        final Map<String, Long> joins = new TreeMap<>();
        for (Map.Entry<String, List<ExecContextData.TaskVertex>> en : byCtx.entrySet()) {
            final String ctx = en.getKey();
            final ExecContextData.TaskVertex head = en.getValue().stream()
                    .filter(v -> g.incomingEdgesOf(v).stream().map(g::getEdgeSource).noneMatch(s -> ctx.equals(s.taskContextId)))
                    .findFirst().orElseThrow();
            if (g.incomingEdgesOf(head).isEmpty()) {
                continue;
            }
            final ExecContextData.TaskVertex tail = en.getValue().stream()
                    .filter(v -> g.outgoingEdgesOf(v).stream().map(g::getEdgeTarget).noneMatch(t -> ctx.equals(t.taskContextId)))
                    .findFirst().orElseThrow();
            final List<Long> into = g.outgoingEdgesOf(tail).stream().map(g::getEdgeTarget)
                    .filter(t -> !ctx.equals(t.taskContextId))
                    .filter(t -> g.incomingEdgesOf(t).stream().map(g::getEdgeSource).anyMatch(s -> t.taskContextId.equals(s.taskContextId)))
                    .map(t -> t.taskId)
                    .toList();
            if (into.size() != 1) {
                throw new IllegalStateException("line " + ctx + " tail #" + tail.taskId + " has " + into.size() + " join edges: " + into);
            }
            joins.put(ctx, into.getFirst());
        }
        return joins;
    }
}
