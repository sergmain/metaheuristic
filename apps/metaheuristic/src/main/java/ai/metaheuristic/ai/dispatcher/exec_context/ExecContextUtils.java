/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 11/14/2020
 * Time: 8:20 PM
 */
public class ExecContextUtils {

    @SneakyThrows
    public static ExecContextApiData.ExecContextVariableStates getExecContextTasksStatesInfo(@Nullable String tasksStatesInfo) {
        ExecContextApiData.ExecContextVariableStates info;
        if (S.b(tasksStatesInfo)) {
            info = new ExecContextApiData.ExecContextVariableStates();
        }
        else {
            info = JsonUtils.getMapper().readValue(tasksStatesInfo, ExecContextApiData.ExecContextVariableStates.class);
        }
        return info;
    }

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
        Map<String, List<ExecContextApiData.VariableState>> map = new HashMap<>();
        for (ExecContextApiData.VariableState info : raw.infos) {
            contexts.add(info.taskContextId);
            map.computeIfAbsent(info.taskContextId, (o) -> new ArrayList<>()).add(info);
        }
        r.header = raw.processCodes.stream().map(o -> new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);
        r.lines = new ExecContextApiData.LineWithState[contexts.size()];

        List<String> sortedContexts = contexts.stream()
                .sorted(ExecContextUtils::compare).collect(Collectors.toList());

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

        for (ExecContextApiData.VariableState taskInfo : raw.infos) {
            for (int i = 0; i < r.lines.length; i++) {
                ExecContextApiData.VariableState simpleTaskInfo = null;
                List<ExecContextApiData.VariableState> tasksInContext = map.get(r.lines[i].context);
                for (ExecContextApiData.VariableState contextTaskInfo : tasksInContext) {
                    if (contextTaskInfo.taskId.equals(taskInfo.taskId)) {
                        simpleTaskInfo = contextTaskInfo;
                        break;
                    }
                }
                if (simpleTaskInfo == null) {
                    continue;
                }
                int j = findOrAssignCol(r.header, simpleTaskInfo.process);

                TaskApiData.TaskState state = raw.states.get(simpleTaskInfo.taskId);
                String stateAsStr;
                List<ExecContextApiData.VariableInfo> outputs = null;
                if (state==null) {
                    stateAsStr = "<ILLEGAL STATE>";
                }
                else {
                    EnumsApi.TaskExecState taskExecState = EnumsApi.TaskExecState.from(state.execState);
                    stateAsStr = taskExecState.toString();

                    if (managerRole && (taskExecState==EnumsApi.TaskExecState.OK || taskExecState== EnumsApi.TaskExecState.ERROR)) {
                        outputs = simpleTaskInfo.outputs;
                    }
                }
                r.lines[i].cells[j] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, stateAsStr, simpleTaskInfo.taskContextId, outputs);
            }
        }
        return r;
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

    private static int findOrAssignCol(ExecContextApiData.ColumnHeader[] headers, String process) {
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
