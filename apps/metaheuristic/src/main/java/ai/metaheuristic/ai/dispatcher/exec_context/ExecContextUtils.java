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
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import org.apache.commons.lang3.StringUtils;

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
        for (TaskApiData.TaskState taskState : raw.taskStates.values()) {
            contexts.add(taskState.taskContextId());
        }

        Map<String, List<ExecContextApiData.VariableState>> map = new HashMap<>();
        for (ExecContextApiData.VariableState info : raw.variableStates) {
            map.computeIfAbsent(info.taskContextId, (o) -> new ArrayList<>()).add(info);
        }
        r.header = raw.processCodes.stream().map(o -> new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);

        // Option 5d: when columnNames is present, override header with dynamic column names
        if (raw.columnNames!=null && !raw.columnNames.isEmpty()) {
            r.header = new ExecContextApiData.ColumnHeader[raw.columnNames.size()];
            for (Map.Entry<Integer, String> entry : raw.columnNames.entrySet()) {
                r.header[entry.getKey()] = new ExecContextApiData.ColumnHeader(entry.getValue(), entry.getValue());
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
            boolean fromCache = state.fromCache();
            if (managerRole && variableState!=null && (taskExecState==EnumsApi.TaskExecState.OK || taskExecState==EnumsApi.TaskExecState.ERROR)) {
                outputs = variableState.outputs;
            }

            // find the line (row) for this task's context
            for (int i = 0; i < r.lines.length; i++) {
                if (r.lines[i].context.equals(taskContextId)) {
                    // TODO 2023-06-07 p5 add input variables' states here
                    r.lines[i].cells[j] = new ExecContextApiData.StateCell(taskId, stateAsStr, taskContextId, fromCache, outputs);
                    break;
                }
            }
        }
        // Option 5d: skip shortName truncation when columnNames provides display names
        if (raw.columnNames==null || raw.columnNames.isEmpty()) {
            for (ExecContextApiData.ColumnHeader ch : r.header) {
                ch.process = ExecContextUtils.shortName(ch.process);
                ch.functionCode = ExecContextUtils.shortName(ch.functionCode);
            }
        }
        return r;
    }

    public static final int MAX_NAME_LENGTH = 15;
    private static String shortName(String s) {
        return StringUtils.substring(s, 0, MAX_NAME_LENGTH) + (s.length()>MAX_NAME_LENGTH ? " ..." : "");
    }

    public static int compare(String o1, String o2) {
        int i1 = o1.indexOf(ContextUtils.CONTEXT_SEPARATOR);
        int i2 = o2.indexOf(ContextUtils.CONTEXT_SEPARATOR);

        String s1 = i1!=-1 ? StringUtils.substring(o1, 0, i1) : o1;
        String s2 = i2!=-1 ? StringUtils.substring(o2, 0, i2) : o2;

        if (s1.equals(s2)) {
            if (i1!=-1 && i2!=-1) {
                int sc1 = Integer.parseInt(o1.substring(i1+ContextUtils.CONTEXT_SEPARATOR.length()));
                int sc2 = Integer.parseInt(o2.substring(i2+ContextUtils.CONTEXT_SEPARATOR.length()));
                return Integer.compare(sc1, sc2);
            }
            if (i1==-1 && i2==-1) {
                return 0;
            }
            if (i1==-1) {
                return -1;
            }
            else {
                return 0;
            }
        }
        String[] sa1 = StringUtils.split(s1, ',');
        String[] sa2 = StringUtils.split(s2, ',');
        int minLen = Math.min(sa1.length, sa2.length);
        for (int i = 0; i < minLen; i++) {
            int v1 = Integer.parseInt(sa1[i]);
            int v2 = Integer.parseInt(sa2[i]);
            int compare = Integer.compare(v1, v2);
            if (compare!=0) {
                return compare;
            }
        }
        return sa1.length > sa2.length ? 1 : -1;
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

        for (String ctx : contexts) {
            String parent = ContextUtils.deriveParentTaskContextId(ctx);
            if (parent == null || !contexts.contains(parent)) {
                // No parent in the set, or parent doesn't exist — treat as root
                // But try to find the nearest existing ancestor
                String nearestAncestor = findNearestAncestor(ctx, contexts);
                if (nearestAncestor != null) {
                    childrenByParent.computeIfAbsent(nearestAncestor, k -> new ArrayList<>()).add(ctx);
                }
                else {
                    roots.add(ctx);
                }
            }
            else {
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(ctx);
            }
        }

        // Sort roots and children within each group using the existing compare method
        List<String> sortedRoots = roots.stream().sorted(ExecContextUtils::compare).collect(Collectors.toList());
        for (List<String> children : childrenByParent.values()) {
            children.sort(ExecContextUtils::compare);
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
     * Walks up the parent chain until an ancestor is found in the set, or returns null.
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
