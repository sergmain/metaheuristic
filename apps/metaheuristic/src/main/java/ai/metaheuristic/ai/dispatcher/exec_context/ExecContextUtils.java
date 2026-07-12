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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 11/14/2020
 * Time: 8:20 PM
 */
public class ExecContextUtils {

    public static List<Long> getIdsForSearch(List<ExecContextData.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()<=fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    public static ExecContextApiData.ExecContextStateResult getExecContextStateResult(Long execContextId, ExecContextApiData.RawExecContextStateResult raw, boolean managerRole) {

        ExecContextApiData.ExecContextStateResult r = new ExecContextApiData.ExecContextStateResult();
        r.sourceCodeId = raw.sourceCodeId;
        r.sourceCodeType = raw.sourceCodeType;
        r.sourceCodeUid = raw.sourceCodeUid;
        r.sourceCodeValid = raw.sourceCodeValid;
        r.execContextId = execContextId;

        Set<String> contexts = new HashSet<>();
        // Track taskId→displayContext mapping for cases where multiple tasks share the same
        // (taskContextId, processCode) — e.g. when two subProcess branches have the same taskContextId.
        // In that case, we create separate display rows with disambiguating suffixes.
        Map<Long, String> taskDisplayContext = new HashMap<>();

        // Group tasks by (taskContextId, column/processCode) to detect collisions
        Map<String, Map<String, List<Long>>> ctxProcessToTasks = new HashMap<>();
        for (Map.Entry<Long, TaskApiData.TaskState> entry : raw.taskStates.entrySet()) {
            Long taskId = entry.getKey();
            TaskApiData.TaskState state = entry.getValue();
            ctxProcessToTasks
                    .computeIfAbsent(state.taskContextId(), k -> new HashMap<>())
                    .computeIfAbsent(state.processCode(), k -> new ArrayList<>())
                    .add(taskId);
        }

        // For each task, determine the display context — append " [#N]" suffix if collisions exist
        for (Map.Entry<String, Map<String, List<Long>>> ctxEntry : ctxProcessToTasks.entrySet()) {
            String taskContextId = ctxEntry.getKey();
            boolean hasCollision = ctxEntry.getValue().values().stream().anyMatch(taskIds -> taskIds.size() > 1);

            if (!hasCollision) {
                // No collision — all tasks in this context get the original context string
                contexts.add(taskContextId);
                for (List<Long> taskIds : ctxEntry.getValue().values()) {
                    for (Long taskId : taskIds) {
                        taskDisplayContext.put(taskId, taskContextId);
                    }
                }
            }
            else {
                // Collision detected — group tasks by their taskId to create separate display rows.
                // Collect all unique taskIds in this context across all processCodes.
                Set<Long> allTaskIdsInCtx = new LinkedHashSet<>();
                for (List<Long> taskIds : ctxEntry.getValue().values()) {
                    allTaskIdsInCtx.addAll(taskIds);
                }
                // Sort taskIds to get stable ordering
                List<Long> sortedTaskIds = allTaskIdsInCtx.stream().sorted().toList();

                // We need to figure out which tasks belong to the same "branch".
                // Tasks from the same branch share the same (taskContextId, processCode) slot
                // but we need to separate branches. Group by the set of processCodes a task appears in.
                // Simpler approach: just use taskId ranges — the old branch has lower taskIds, new branch has higher.
                // Even simpler: for each colliding processCode, assign tasks in order to separate display contexts.

                // Build branch assignment: for each colliding processCode, tag each task with its branch index
                Map<Long, Integer> taskBranchIndex = new HashMap<>();
                for (Map.Entry<String, List<Long>> processEntry : ctxEntry.getValue().entrySet()) {
                    List<Long> taskIds = processEntry.getValue();
                    if (taskIds.size() > 1) {
                        List<Long> sorted = taskIds.stream().sorted().toList();
                        for (int idx = 0; idx < sorted.size(); idx++) {
                            // Use max branch index seen so far for this task
                            taskBranchIndex.merge(sorted.get(idx), idx, Math::max);
                        }
                    }
                }

                // Assign display contexts
                for (Long taskId : sortedTaskIds) {
                    int branchIdx = taskBranchIndex.getOrDefault(taskId, 0);
                    String displayCtx = branchIdx == 0 ? taskContextId : taskContextId + " [#" + branchIdx + "]";
                    contexts.add(displayCtx);
                    taskDisplayContext.put(taskId, displayCtx);
                }
            }
        }

        Map<String, List<ExecContextApiData.VariableState>> map = new HashMap<>();
        for (ExecContextApiData.VariableState info : raw.variableStates) {
            map.computeIfAbsent(info.taskContextId, (o) -> new ArrayList<>()).add(info);
        }
        // DSL v2: recursive-group body processes are grafted at runtime and are absent from the static
        // process topology (raw.processCodes). Append any task processCode not already present so every
        // grafted task gets a column; otherwise the legacy findOrAssignCol path throws "(idx==-1)".
        LinkedHashSet<String> effectiveProcessCodes = new LinkedHashSet<>();
        if (raw.taskEdges != null && !raw.taskEdges.isEmpty()) {
            // The static topology (raw.processCodes) is authoritative for the relative order of static
            // processes — it already places control processes (e.g. the graft node, mh.finish) and
            // mhdg-rg.post-processing correctly. Only runtime-grafted (DSL v2 recursive-group) body
            // processes are missing from it; each is inserted right after its nearest static ancestor
            // in the real task DAG, instead of being appended after the terminal columns. This keeps a
            // static leaf like a graft node in its authored position rather than floating it to the end.
            List<String> staticOrder = raw.processCodes;
            Set<String> staticSet = new HashSet<>(staticOrder);
            Map<String, Integer> staticIndex = new HashMap<>();
            for (int i = 0; i < staticOrder.size(); i++) {
                staticIndex.put(staticOrder.get(i), i);
            }

            DirectedAcyclicGraph<Long, DefaultEdge> taskGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
            for (Long taskId : raw.taskStates.keySet()) {
                taskGraph.addVertex(taskId);
            }
            for (long[] edge : raw.taskEdges) {
                taskGraph.addVertex(edge[0]);
                taskGraph.addVertex(edge[1]);
                if (edge[0] != edge[1]) {
                    try {
                        taskGraph.addEdge(edge[0], edge[1]);
                    }
                    catch (IllegalArgumentException e) {
                        // defensive: skip any edge that would introduce a cycle. The ExecContext task
                        // graph is a DAG, so this is not expected for real data.
                    }
                }
            }

            // Walk tasks in topological order (tie-break by task-id). For each task track the nearest
            // static process on the path to it; for a grafted process, record the static anchor it
            // should follow (first occurrence wins). Grafted siblings keep topological discovery order.
            Map<Long, String> nearestStatic = new HashMap<>();
            LinkedHashMap<String, String> graftedAnchor = new LinkedHashMap<>();
            TopologicalOrderIterator<Long, DefaultEdge> it = new TopologicalOrderIterator<>(taskGraph, Comparator.naturalOrder());
            while (it.hasNext()) {
                Long taskId = it.next();
                TaskApiData.TaskState st = raw.taskStates.get(taskId);
                if (st == null) {
                    continue;
                }
                String proc = st.processCode();
                if (staticSet.contains(proc)) {
                    nearestStatic.put(taskId, proc);
                }
                else {
                    // inherit the nearest static ancestor from predecessors — the one latest in static order
                    String anchor = null;
                    int bestIdx = -1;
                    for (DefaultEdge in : taskGraph.incomingEdgesOf(taskId)) {
                        String ps = nearestStatic.get(taskGraph.getEdgeSource(in));
                        if (ps != null) {
                            int idx = staticIndex.getOrDefault(ps, -1);
                            if (idx > bestIdx) {
                                bestIdx = idx;
                                anchor = ps;
                            }
                        }
                    }
                    nearestStatic.put(taskId, anchor);
                    if (anchor != null) {
                        graftedAnchor.putIfAbsent(proc, anchor);
                    }
                }
            }

            // group grafted processes by their static anchor, preserving topological discovery order
            LinkedHashMap<String, List<String>> byAnchor = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : graftedAnchor.entrySet()) {
                byAnchor.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
            // emit the static backbone, inserting grafted processes right after their anchor
            for (String staticProc : staticOrder) {
                effectiveProcessCodes.add(staticProc);
                List<String> inserted = byAnchor.get(staticProc);
                if (inserted != null) {
                    effectiveProcessCodes.addAll(inserted);
                }
            }
            // safety net: ensure every task process code has a column (grafted with no static ancestor,
            // or a static process that produced no task) — append any still missing by task-id order
            raw.taskStates.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> effectiveProcessCodes.add(e.getValue().processCode()));
        }
        else {
            effectiveProcessCodes.addAll(raw.processCodes);
            raw.taskStates.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> effectiveProcessCodes.add(e.getValue().processCode()));
        }
        r.header = effectiveProcessCodes.stream().map(o -> new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);

        // Option 5d: when columnNames is present, override header with dynamic column names
        if (raw.columnNames!=null && !raw.columnNames.isEmpty()) {
            r.header = new ExecContextApiData.ColumnHeader[raw.columnNames.size()];
            for (Map.Entry<Integer, String> entry : raw.columnNames.entrySet()) {
                r.header[entry.getKey()] = new ExecContextApiData.ColumnHeader(entry.getValue(), entry.getValue());
            }
        }

        // Apply process tags to column headers
        if (raw.processTags != null) {
            for (ExecContextApiData.ColumnHeader h : r.header) {
                if (h != null && h.process != null) {
                    String tag = raw.processTags.get(h.process);
                    if (tag != null) {
                        h.tags = tag.split(",");
                    }
                }
            }
        }

        r.lines = new ExecContextApiData.LineWithState[contexts.size()];

        //noinspection SimplifyStreamApiCallChains
        List<String> sortedContexts = buildHierarchicalContextOrder(contexts);

        for (int i = 0; i < r.lines.length; i++) {
            r.lines[i] = new ExecContextApiData.LineWithState();
        }
        for (ExecContextApiData.LineWithState line : r.lines) {
            line.cells = new ExecContextApiData.StateCell[r.header.length];
            for (int i = 0; i < r.header.length; i++) {
                line.cells[i] = new ExecContextApiData.StateCell();
            }
        }
        for (int i = 0; i < r.lines.length; i++) {
            r.lines[i].context = sortedContexts.get(i);
        }

        // Option 5d: build processCode-to-column-index map when columnNames is present
        final boolean useColumnNames = raw.columnNames!=null && !raw.columnNames.isEmpty();
        Map<String, Integer> processCodeToColIdx = null;
        if (useColumnNames) {
            processCodeToColIdx = new HashMap<>();
            for (int idx = 0; idx < raw.processCodes.size(); idx++) {
                processCodeToColIdx.put(raw.processCodes.get(idx), idx);
            }
        }

        for (Map.Entry<Long, TaskApiData.TaskState> entry : raw.taskStates.entrySet()) {
            Long taskId = entry.getKey();
            TaskApiData.TaskState state = entry.getValue();
            String taskContextId = state.taskContextId();
            EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(state.execState());
            String processCode = state.processCode();

            // find VariableState for this task (may be null for tasks SKIPPED before variable init)
            ExecContextApiData.VariableState variableState = null;
            List<ExecContextApiData.VariableState> tasksInContext = map.get(taskContextId);
            if (tasksInContext!=null) {
                for (ExecContextApiData.VariableState vs : tasksInContext) {
                    if (vs.taskId.equals(taskId)) {
                        variableState = vs;
                        break;
                    }
                }
            }

            // Option 5d: when columnNames is present, resolve column by processCode index; otherwise use legacy findOrAssignCol
            int j;
            if (useColumnNames && processCodeToColIdx!=null) {
                Integer colIdx = processCodeToColIdx.get(processCode);
                if (colIdx==null) {
                    throw new IllegalStateException("Process code '" + processCode + "' not found in processCodes list");
                }
                j = colIdx;
            }
            else {
                j = findOrAssignCol(r.header, processCode);
            }

            String stateAsStr = taskExecState.toString();
            List<ExecContextApiData.VariableInfo> outputs = null;
            List<ExecContextApiData.VariableInfo> inputs = null;
            boolean fromCache = state.fromCache();
            if (managerRole && variableState!=null && (taskExecState==EnumsApi.TaskExecState.OK || taskExecState==EnumsApi.TaskExecState.ERROR)) {
                outputs = variableState.outputs;
                inputs = variableState.inputs;
            }

            // find the line (row) for this task's display context
            String displayCtx = taskDisplayContext.getOrDefault(taskId, taskContextId);
            for (int i = 0; i < r.lines.length; i++) {
                if (r.lines[i].context.equals(displayCtx)) {
                    // TODO 2023-06-07 p5 add input variables' states here
                    r.lines[i].cells[j] = new ExecContextApiData.StateCell(taskId, stateAsStr, taskContextId, fromCache, inputs, outputs);
                    break;
                }
            }
        }
        // shortName truncation moved to UI side
        r.taskEdges = raw.taskEdges;
        return r;
    }

    private static final String DISPLAY_SUFFIX_PREFIX = " [#";

    /**
     * Strips the display disambiguation suffix " [#N]" from a context string.
     * Returns the original taskContextId suitable for ContextUtils operations.
     */
    private static String stripDisplaySuffix(String displayCtx) {
        int idx = displayCtx.indexOf(DISPLAY_SUFFIX_PREFIX);
        return idx == -1 ? displayCtx : displayCtx.substring(0, idx);
    }

    public static int compare(String o1, String o2) {
        return ContextUtils.compareTaskContextIds(stripDisplaySuffix(o1), stripDisplaySuffix(o2));
    }

    // public for testing - will be refactored in Option 5d
    public static int findOrAssignCol(ExecContextApiData.ColumnHeader[] headers, String process) {
        int idx = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].process == null) {
                if (idx == -1) {
                    idx = i;
                }
                continue;
            }
            if (process.equals(headers[i].process)) {
                return i;
            }
        }
        if (idx == -1) {
            throw new IllegalStateException("(idx==-1)");
        }
        headers[idx].process = process;
        return idx;
    }

    /**
     * Builds a hierarchical ordering of taskContextIds where children are grouped
     * immediately after their parent, producing an interleaved tree-walk order.
     *
     * Example: instead of flat [1, 1,2#1, 1,2#2, 1,2,3,1#0, 1,2,3,2#0]
     * produces: [1, 1,2#1, 1,2,3,1#0, 1,2#2, 1,2,3,2#0]
     */
    public static List<String> buildHierarchicalContextOrder(Set<String> contexts) {
        // Build parent-child mapping
        Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
        Set<String> roots = new LinkedHashSet<>();

        // Build a set of stripped contexts for parent lookup
        Map<String, String> strippedToDisplay = new LinkedHashMap<>();
        for (String ctx : contexts) {
            String stripped = stripDisplaySuffix(ctx);
            // If multiple display contexts map to the same stripped context,
            // keep one mapping for parent resolution (the first one seen)
            strippedToDisplay.putIfAbsent(stripped, ctx);
        }
        Set<String> strippedContexts = strippedToDisplay.keySet();

        for (String ctx : contexts) {
            String stripped = stripDisplaySuffix(ctx);
            String parent = ContextUtils.deriveParentTaskContextId(stripped);
            if (parent == null || !strippedContexts.contains(parent)) {
                String nearestAncestor = findNearestAncestor(stripped, strippedContexts);
                if (nearestAncestor != null) {
                    // Find the display context for the nearest ancestor
                    String ancestorDisplay = strippedToDisplay.getOrDefault(nearestAncestor, nearestAncestor);
                    childrenByParent.computeIfAbsent(ancestorDisplay, k -> new ArrayList<>()).add(ctx);
                }
                else {
                    roots.add(ctx);
                }
            }
            else {
                String parentDisplay = strippedToDisplay.getOrDefault(parent, parent);
                childrenByParent.computeIfAbsent(parentDisplay, k -> new ArrayList<>()).add(ctx);
            }
        }

        // Sort roots and children within each group in ascending order for visualization
        // (ExecContextUtils::compare is descending, so we reverse it)
        java.util.Comparator<String> ascendingComparator = (a, b) -> ExecContextUtils.compare(b, a);
        List<String> sortedRoots = roots.stream().sorted(ascendingComparator).collect(Collectors.toList());
        for (List<String> children : childrenByParent.values()) {
            children.sort(ascendingComparator);
        }

        // Recursive tree walk
        List<String> result = new ArrayList<>();
        for (String root : sortedRoots) {
            emitWithChildren(root, childrenByParent, result);
        }
        return result;
    }

    private static void emitWithChildren(String context, Map<String, List<String>> childrenByParent, List<String> result) {
        result.add(context);
        List<String> children = childrenByParent.get(context);
        if (children != null) {
            for (String child : children) {
                emitWithChildren(child, childrenByParent, result);
            }
        }
    }

    /**
     * Finds the nearest existing ancestor of a taskContextId within the given set.
     * Walks up the parent chain via deriveParentTaskContextId until an ancestor
     * is found in the set, or returns null.
     *
     * With the new '|' format, deriveParentTaskContextId is unambiguous at any depth,
     * so this walk should always find the correct parent if it exists in the set.
     */
    @org.jspecify.annotations.Nullable
    private static String findNearestAncestor(String taskContextId, Set<String> contexts) {
        String current = taskContextId;
        for (int i = 0; i < 20; i++) { // safety limit to prevent infinite loops
            String parent = ContextUtils.deriveParentTaskContextId(current);
            if (parent == null) {
                return null;
            }
            if (contexts.contains(parent)) {
                return parent;
            }
            current = parent;
        }
        return null;
    }
}
