/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.source_code.SourceCodeApiData.ExecContextForDeletion;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextCache execContextCache;
    private final ExecContextService execContextService;
    private final ExecContextGraphService execContextGraphService;
    private final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskService taskService;

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = execContextService.getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        result.sourceCodeId = sourceCodeId;
        initInfoAboutSourceCode(sourceCodeId, result);
        return result;
    }

    private void initInfoAboutSourceCode(Long sourceCodeId, ExecContextApiData.ExecContextsResult result) {
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc!=null) {
            result.sourceCodeUid=sc.uid;
            result.sourceCodeValid=sc.valid;
            result.sourceCodeType = getType(sc.uid);
        }
        else {
            result.sourceCodeUid = "SourceCode was deleted";
            result.sourceCodeValid = false;
            result.sourceCodeType = EnumsApi.SourceCodeType.not_exist;
        }
    }

    private EnumsApi.SourceCodeType getType(String uid) {
        if (dispatcherParamsService.getBatches().contains(uid)) {
            return EnumsApi.SourceCodeType.batch;
        }
        else if (dispatcherParamsService.getExperiments().contains(uid)) {
            return EnumsApi.SourceCodeType.experiment;
        }
        return EnumsApi.SourceCodeType.common;
    }

    public ExecContextApiData.ExecContextStateResult getExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.sourceCodeId = sourceCodeId;
        initInfoAboutSourceCode(sourceCodeId, result);

        ExecContextApiData.ExecContextStateResult r = new ExecContextApiData.ExecContextStateResult();
        r.sourceCodeId = sourceCodeId;
        r.sourceCodeType = result.sourceCodeType;
        r.sourceCodeUid = result.sourceCodeUid;
        r.sourceCodeValid = result.sourceCodeValid;

        List<TaskData.SimpleTaskInfo> infos = taskService.getSimpleTaskInfos(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            ExecContextApiData.ExecContextStateResult resultWithError = new ExecContextApiData.ExecContextStateResult();
            resultWithError.addErrorMessage("Can't find execContext for Id "+ execContextId);
            return resultWithError;
        }
        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();


        Set<String> contexts = new HashSet<>();
        Set<String> processes = new HashSet<>();
        Map<String, List<TaskData.SimpleTaskInfo>> map = new HashMap<>();
        for (TaskData.SimpleTaskInfo info : infos) {
            contexts.add(info.context);
            processes.add(info.process);

            map.computeIfAbsent(info.context, (o)->new ArrayList<>()).add(info);
        }
        r.header = contexts.stream().sorted(String::compareTo).toArray(String[]::new);
        r.lines = new ExecContextApiData.LineWithState[processes.size()];
        for (int i = 0; i < r.lines.length; i++) {
            r.lines[i] = new ExecContextApiData.LineWithState();
        }
        for (ExecContextApiData.LineWithState line : r.lines) {
            line.cells = new ExecContextApiData.StateCell[r.header.length];
            for (int i = 0; i < r.header.length; i++) {
                line.cells[i] = new ExecContextApiData.StateCell();
            }
        }
        // mh.finish is the last always
        // but process can be named differently
        boolean finishIsLast = false;
        if (processes.contains(Consts.MH_FINISH_FUNCTION)) {
            r.lines[r.lines.length - 1].lineHeader = new ExecContextApiData.LineHeader(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION);
            finishIsLast = true;
        }

        List<List<ExecContextData.TaskVertex>> vertices = execContextGraphService.graphAsListOfLIst(ec);

        // find all processes which is just before mh.finish
        List<ExecContextData.TaskVertex> leafs = execContextGraphService.findLeafs(ec);
        Set<ExecContextData.TaskVertex> beforeFinishVertices = new HashSet<>();
        for (ExecContextData.TaskVertex leaf : leafs) {
            beforeFinishVertices.addAll(execContextGraphService.findDirectAncestors(ec, leaf));
        }
        Set<Long> beforeFinishIds = beforeFinishVertices.stream().map(o->o.taskId).collect(Collectors.toSet());


        Set<String> beforeProcesses = new HashSet<>();

        for (List<ExecContextData.TaskVertex> vertex : vertices) {
            for (int i = 0; i < r.header.length; i++) {
                ExecContextData.TaskVertex v = null;
                TaskData.SimpleTaskInfo simpleTaskInfo = null;
                List<TaskData.SimpleTaskInfo> simpleTaskInfos = map.get(r.header[i]);
                for (ExecContextData.TaskVertex taskVertex : vertex) {
                    for (TaskData.SimpleTaskInfo info : simpleTaskInfos) {
                        if (info.taskId.equals(taskVertex.taskId)) {
                            simpleTaskInfo = info;
                            v = taskVertex;
                            break;
                        }
                    }
                    if (simpleTaskInfo!=null) {
                        break;
                    }
                }
                if (simpleTaskInfo==null) {
                    continue;
                }
                int j = findRow(r.lines, new ExecContextApiData.LineHeader(simpleTaskInfo.process, simpleTaskInfo.functionCode));
                r.lines[j].cells[i] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, simpleTaskInfo.state, simpleTaskInfo.context);
                if (beforeFinishIds.contains(simpleTaskInfo.taskId)) {
                    beforeProcesses.add(simpleTaskInfo.process);
                }
            }
        }

        int idx = 0;
        int shift = finishIsLast ? 1 : 0;
        for (String process : beforeProcesses) {
            for (int i = 0; i < r.lines.length - beforeProcesses.size() - shift ; i++) {
                if (r.lines[i].lineHeader.process.equals(process)) {
                    ExecContextApiData.LineWithState l = r.lines[i];
                    r.lines[i] = r.lines[r.lines.length - beforeProcesses.size() - shift + idx];
                    r.lines[r.lines.length - beforeProcesses.size() - shift + idx] = l;
                    idx++;
                }
            }
        }

        return r;
    }

    private int findRow(ExecContextApiData.LineWithState[] lines, ExecContextApiData.LineHeader lineHeader) {
        int idx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].lineHeader==null) {
                if (idx==-1) {
                    idx = i;
                }
                continue;
            }
            if (lineHeader.process.equals(lines[i].lineHeader.process)) {
                return i;
            }
        }
        if (idx==-1) {
            throw new IllegalStateException("(idx==-1)");
        }
        lines[idx].lineHeader = lineHeader;
        return idx;
    }

    public ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new ExecContextForDeletion("#778.020 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        ExecContextForDeletion result = new ExecContextForDeletion(execContext.sourceCodeId, execContext.id, ecpy.sourceCodeUid, EnumsApi.ExecContextState.from(execContext.state));
        return result;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        if (execContextId==null) {
            return new SourceCodeApiData.ExecContextResult("#705.160 execContextId is null");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
            execContextService.changeValidStatus(execContextId, false);
            return new SourceCodeApiData.ExecContextResult("#705.220 sourceCodeId doesn't match to execContext.sourceCodeId, sourceCodeId: " + execContext.getSourceCodeId()+", execContext.sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

}
