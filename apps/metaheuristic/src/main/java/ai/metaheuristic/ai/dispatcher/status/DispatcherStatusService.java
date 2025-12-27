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

package ai.metaheuristic.ai.dispatcher.status;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.StatusUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.account.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Sergio Lissner
 * Date: 10/28/2023
 * Time: 9:07 PM
 */
@Slf4j
@Service
@Profile({"dispatcher"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DispatcherStatusService {

    private final SourceCodeRepository sourceCodeRepository;
    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final TaskRepository taskRepository;
    private final ExecContextTopLevelService execContextTopLevelService;

    public String statusSourceCode(Long id, UserContext context) {
        SourceCodeImpl sc = sourceCodeRepository.findById(id).orElse(null);
        if (sc==null) {
            return "SourceCode with id #"+id+" wasn't found\n";
        }
        StringBuilder s = new StringBuilder(S.f("""
                SourceCode: #%d, uid: %s, valid: %b
                ExecContexts:
                """, sc.id, sc.uid, sc.valid));
        for (ExecContextImpl ec : execContextRepository.findBySourceCodeId(sc.id)) {
            s.append(S.f("  %5d %s\n", ec.id, EnumsApi.ExecContextState.toState(ec.state)));
        }
        return s.toString();
    }

    public String statusExecContext(Long id, UserContext context, Authentication authentication) {
        ExecContextImpl ec = execContextRepository.findById(id).orElse(null);
        if (ec == null) {
            return "ExecContext with id #" + id + " wasn't found\n";
        }
        SourceCodeImpl sc = sourceCodeRepository.findById(ec.sourceCodeId).orElse(null);
        if (sc == null) {
            return "In ExecContext #" + id + ", SourceCode with id #" + ec.sourceCodeId + " wasn't found\n";
        }
        ExecContextGraph ecg = execContextGraphRepository.findById(ec.execContextGraphId).orElse(null);
        if (ecg == null) {
            return "In ExecContext #" + id + ", ExecContextGraph with id #" + ec.execContextGraphId + " wasn't found\n";
        }
        ExecContextTaskState ects = execContextTaskStateRepository.findById(ec.execContextTaskStateId).orElse(null);
        if (ects == null) {
            return "In ExecContext #" + id + ", ExecContextTaskState with id #" + ec.execContextTaskStateId + " wasn't found\n";
        }
        ExecContextVariableState ecvs = execContextVariableStateRepository.findById(ec.execContextVariableStateId).orElse(null);
        if (ecvs == null) {
            return "In ExecContext #" + id + ", ExecContextVariableState with id #" + ec.execContextVariableStateId + " wasn't found\n";
        }
        StringBuilder s = new StringBuilder(S.f("""
            SourceCode: #%d, uid: %s, valid: %b
            ExecContext: #%d, %s
            """, sc.id, sc.uid, sc.valid, ec.id, EnumsApi.ExecContextState.toState(ec.state)));

        ExecContextGraphParamsYaml ecgParams = ecg.getExecContextGraphParamsYaml();
        s.append(S.f("""
              Graph #%d
            %s
            """, ecg.id, ecgParams.graph.indent(2)));

        ExecContextApiData.ExecContextStateResult execContextState = execContextTopLevelService.getExecContextState(sc.id, ec.id, authentication);
        if (execContextState.taskStateInfos!=null) {
            s.append("Task count by TaskState\n");
            for (ExecContextApiData.TaskStateInfo taskInfo : execContextState.taskStateInfos.taskInfos) {
                s.append(S.f("%s: %d\n", taskInfo.execState, taskInfo.count));
            }
            s.append('\n');
        }
        String[][] tbl = asStringTable(execContextState.header, execContextState.lines);
        StatusUtils.printTable(s::append, true, 10, false, tbl);

        ExecContextApiData.ExecContextVariableStates varStates = ecvs.getExecContextVariableStateInfo();
        ExecContextTaskStateParamsYaml taskStateParams = ects.getExecContextTaskStateParamsYaml();
        for (Map.Entry<Long, EnumsApi.TaskExecState> en : taskStateParams.states.entrySet()) {
            TaskImpl task = taskRepository.findById(en.getKey()).orElse(null);
            if (task!=null) {
                var funcCode = task.getTaskParamsYaml().task.function.code;
                s.append(S.f("  Task: #%d, %s. Func: %s\n", en.getKey(), en.getValue(), funcCode));
            }
            else {
                s.append(S.f("  Task: #%d, %s. Task wasn't found with this Id\n", en.getKey(), en.getValue()));
            }

            ExecContextApiData.VariableState variableState = varStates.states.stream().filter(o -> o.taskId.equals(en.getKey())).findFirst().orElse(null);
            if (variableState == null || (CollectionUtils.isEmpty(variableState.inputs) && CollectionUtils.isEmpty(variableState.outputs))) {
                continue;
            }
            if (CollectionUtils.isNotEmpty(variableState.inputs)) {
                s.append("    In:\n");
                for (ExecContextApiData.VariableInfo input : variableState.inputs) {
                    s.append(S.f("      #%d, %s, inited: %b, nullified: %b\n", input.id, input.name, input.inited, input.nullified));
                }
            }
            if (CollectionUtils.isNotEmpty(variableState.outputs)) {
                s.append("    Out:\n");
                for (ExecContextApiData.VariableInfo output : variableState.outputs) {
                    s.append(S.f("      #%d, %s, inited: %b, nullified: %b\n", output.id, output.name, output.inited, output.nullified));
                }
            }
        }

        return s.toString();
    }

    private static String[][] asStringTable(ExecContextApiData.ColumnHeader[] header, ExecContextApiData.LineWithState[] lines) {
        String[][] ss = new String[lines.length + 1][header.length];
        for (int i = 0; i < header.length; i++) {
            ExecContextApiData.ColumnHeader h = header[i];
            ss[0][i] = h.process+", " + h.functionCode;
        }

        for (int i = 0; i < lines.length; i++) {
            ExecContextApiData.LineWithState l = lines[i];
            for (int j = 0; j < l.cells.length; j++) {
                
/*
                46170, OK
                108431, var-processed-file-a
                108432, var-processed-file-a1
                108433, var-processed-file-b
                108434, var-processing-status
                108435, var-params-yaml
                108436, var-item-mapping
*/
                ExecContextApiData.StateCell c = l.cells[j];
                if (c.empty) {
                    ss[i + 1][j] = "";
                }
                else {
                    String s = "" + c.taskId+": "+ c.state+". ";
                    if (CollectionUtils.isNotEmpty(c.outs)) {
                        for (ExecContextApiData.VariableInfo out : c.outs) {
                            s += ("["+out.id+':'+out.name+", init:"+out.inited+']');
                        }
                    }
                    ss[i + 1][j] = s;
                }
            }
        }
        return ss;
    }


}
