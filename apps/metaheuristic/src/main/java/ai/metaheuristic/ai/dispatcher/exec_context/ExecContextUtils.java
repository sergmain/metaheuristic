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
        List<String> sortedContexts = contexts.stream().sorted(ExecContextUtils::compare).collect(Collectors.toList());

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

        for (ExecContextApiData.VariableState variableState : raw.variableStates) {
            for (int i = 0; i < r.lines.length; i++) {
                ExecContextApiData.VariableState simpleTaskInfo = null;
                List<ExecContextApiData.VariableState> tasksInContext = map.get(r.lines[i].context);
                for (ExecContextApiData.VariableState contextTaskInfo : tasksInContext) {
                    if (contextTaskInfo.taskId.equals(variableState.taskId)) {
                        simpleTaskInfo = contextTaskInfo;
                        break;
                    }
                }
                if (simpleTaskInfo == null) {
                    continue;
                }
                // Option 5d: when columnNames is present, resolve column by processCode index; otherwise use legacy findOrAssignCol
                int j;
                if (useColumnNames && processCodeToColIdx!=null) {
                    Integer colIdx = processCodeToColIdx.get(simpleTaskInfo.process);
                    if (colIdx==null) {
                        throw new IllegalStateException("Process code '" + simpleTaskInfo.process + "' not found in processCodes list");
                    }
                    j = colIdx;
                }
                else {
                    j = findOrAssignCol(r.header, simpleTaskInfo.process);
                }

                TaskApiData.TaskState state = raw.taskStates.get(simpleTaskInfo.taskId);
                String stateAsStr;
                List<ExecContextApiData.VariableInfo> outputs = null;
                boolean fromCache = false;
                if (state==null) {
                    stateAsStr = "<ILLEGAL STATE>";
                }
                else {
                    EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(state.execState());
                    stateAsStr = taskExecState.toString();

                    if (managerRole && (taskExecState==EnumsApi.TaskExecState.OK || taskExecState== EnumsApi.TaskExecState.ERROR)) {
                        outputs = simpleTaskInfo.outputs;
                    }
                    fromCache = state.fromCache();
                }
                // TODO 2023-06-07 p5 add input variables' states here
                r.lines[i].cells[j] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, stateAsStr, simpleTaskInfo.taskContextId, fromCache, outputs);
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
}
