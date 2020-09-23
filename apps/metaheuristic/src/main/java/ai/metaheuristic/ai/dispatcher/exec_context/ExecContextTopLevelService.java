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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
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
    private final SourceCodeCache sourceCodeCache;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskTransactionalService taskTransactionService;

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

        List<TaskData.SimpleTaskInfo> infos = taskTransactionService.getSimpleTaskInfos(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            ExecContextApiData.ExecContextStateResult resultWithError = new ExecContextApiData.ExecContextStateResult();
            resultWithError.addErrorMessage("Can't find execContext for Id "+ execContextId);
            return resultWithError;
        }
        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ec.getExecContextParamsYaml());
        ExecContextApiData.ExecContextStateResult r = getExecContextStateResult(
                sourceCodeId, infos, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid);
        return r;
    }

    public static ExecContextApiData.ExecContextStateResult getExecContextStateResult(
            Long sourceCodeId, List<TaskData.SimpleTaskInfo> infos,
            List<String> processCodes, EnumsApi.SourceCodeType sourceCodeType, String sourceCodeUid, boolean sourceCodeValid) {

        ExecContextApiData.ExecContextStateResult r = new ExecContextApiData.ExecContextStateResult();
        r.sourceCodeId = sourceCodeId;
        r.sourceCodeType = sourceCodeType;
        r.sourceCodeUid = sourceCodeUid;
        r.sourceCodeValid = sourceCodeValid;

        Set<String> contexts = new HashSet<>();
        Map<String, List<TaskData.SimpleTaskInfo>> map = new HashMap<>();
        for (TaskData.SimpleTaskInfo info : infos) {
            contexts.add(info.context);
            map.computeIfAbsent(info.context, (o)->new ArrayList<>()).add(info);
        }
        r.header = processCodes.stream().map(o->new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);
        r.lines = new ExecContextApiData.LineWithState[contexts.size()];

        List<String> sortedContexts = contexts.stream().sorted(String::compareTo).collect(Collectors.toList());
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

        for (TaskData.SimpleTaskInfo taskInfo : infos) {
            for (int i = 0; i < r.lines.length; i++) {
                TaskData.SimpleTaskInfo simpleTaskInfo = null;
                List<TaskData.SimpleTaskInfo> tasksInContext = map.get(r.lines[i].context);
                for (TaskData.SimpleTaskInfo contextTaskInfo : tasksInContext) {
                    if (contextTaskInfo.taskId.equals(taskInfo.taskId)) {
                        simpleTaskInfo = contextTaskInfo;
                        break;
                    }
                }
                if (simpleTaskInfo==null) {
                    continue;
                }
                int j = findCol(r.header, simpleTaskInfo.process);
                r.lines[i].cells[j] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, simpleTaskInfo.state, simpleTaskInfo.context);
            }
        }
        return r;
    }

    private static int findCol(ExecContextApiData.ColumnHeader[] headers, String process) {
        int idx = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].process==null) {
                if (idx==-1) {
                    idx = i;
                }
                continue;
            }
            if (process.equals(headers[i].process)) {
                return i;
            }
        }
        if (idx==-1) {
            throw new IllegalStateException("(idx==-1)");
        }
        headers[idx].process = process;
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
