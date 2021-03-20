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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextTxEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskQueueCleanByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.OperationStatus;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCache execContextCache;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;
    private final ExecContextSyncService execContextSyncService;
    private final EntityManager em;
    private final VariableService variableService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecContextVariableStateCache execContextVariableStateCache;

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        initInfoAboutSourceCode(sourceCodeId, result);
        return result;
    }

    @Nullable
    public ExecContextImpl findById(Long id) {
        return execContextCache.findById(id);
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
                .sorted(ExecContextService::compare).collect(Collectors.toList());

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
        if (i1==-1 && i2!=-1) {
            return -1;
        }

        if (i1!=-1 && i2==-1) {
            return 1;
        }
        return o1.compareTo(o2);
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

    @Transactional
    public Void changeValidStatus(Long execContextId, boolean status) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        execContext.setValid(status);
        save(execContext);
        return null;
    }

    public ExecContextImpl save(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        if (execContext.id!=null) {
            execContextSyncService.checkWriteLockPresent(execContext.id);
        }
        if (execContext.id==null) {
            final ExecContextImpl ec = execContextCache.save(execContext);
            return ec;
        }
        else if (!em.contains(execContext) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("#705.020 Bean %s isn't managed by EntityManager", execContext));
        }
        return execContext;
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.180 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.200 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    public ExecContextApiData.RawExecContextStateResult getRawExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        TxUtils.checkTxNotExists();

        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.assetMode);
        initInfoAboutSourceCode(sourceCodeId, result);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            ExecContextApiData.RawExecContextStateResult resultWithError = new ExecContextApiData.RawExecContextStateResult("#705.220 Can't find execContext for Id " + execContextId);
            return resultWithError;
        }
        ExecContextApiData.ExecContextVariableStates info = getExecContextVariableStates(ec);
        ExecContextParamsYaml ecpy = ec.getExecContextParamsYaml();

        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ecpy);
        return new ExecContextApiData.RawExecContextStateResult(
                sourceCodeId, info.tasks, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid,
                getExecStateOfTasks(execContextId)
        );
    }

    private ExecContextApiData.ExecContextVariableStates getExecContextVariableStates(ExecContextImpl ec) {
        ExecContextApiData.ExecContextVariableStates info;
        if (ec.execContextVariableStateId==null) {
            info = new ExecContextApiData.ExecContextVariableStates();
        }
        else {
            ExecContextVariableState ecvs = execContextVariableStateCache.findById(ec.execContextVariableStateId);
            info = ecvs!=null ? ecvs.getExecContextVariableStateInfo() : new ExecContextApiData.ExecContextVariableStates();
        }
        return info;
    }

    public Map<Long, TaskApiData.TaskState> getExecStateOfTasks(Long execContextId) {
        List<Object[]> list = taskRepository.findExecStateByExecContextId(execContextId);

        Map<Long, TaskApiData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskApiData.TaskState taskState = new TaskApiData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }
        return states;
    }

    private void initInfoAboutSourceCode(Long sourceCodeId, ExecContextApiData.ExecContextsResult result) {
        SourceCodeImpl sc = sourceCodeCache.findById(sourceCodeId);
        if (sc != null) {
            result.sourceCodeUid = sc.uid;
            result.sourceCodeValid = sc.valid;
            result.sourceCodeType = getType(sc.uid);
        } else {
            result.sourceCodeUid = "SourceCode was deleted";
            result.sourceCodeValid = false;
            result.sourceCodeType = EnumsApi.SourceCodeType.not_exist;
        }
    }

    private EnumsApi.SourceCodeType getType(String uid) {
        if (dispatcherParamsService.getBatches().contains(uid)) {
            return EnumsApi.SourceCodeType.batch;
        } else if (dispatcherParamsService.getExperiments().contains(uid)) {
            return EnumsApi.SourceCodeType.experiment;
        }
        return EnumsApi.SourceCodeType.common;
    }

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult(sourceCodeId, globals.assetMode);
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        return result;
    }

    @Transactional
    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
        deleteExecContext(execContextId);
    }

    @Transactional
    public Void deleteExecContext(Long execContextId) {
        eventPublisher.publishEvent(new ProcessDeletedExecContextTxEvent(execContextId));
        eventPublisher.publishEvent(new TaskQueueCleanByExecContextIdEvent(execContextId));
        execContextCache.deleteById(execContextId);
        // tasks and variables will be deleted in another thread launched by Scheduler
        return null;
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextForDeletion("#705.260 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        SourceCodeApiData.ExecContextForDeletion result = new SourceCodeApiData.ExecContextForDeletion(execContext.sourceCodeId, execContext.id, ecpy.sourceCodeUid, EnumsApi.ExecContextState.from(execContext.state));
        return result;
    }

    @Transactional
    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        deleteExecContext(execContextId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#705.280 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }


    public CleanerInfo downloadVariable(Long execContextId, Long variableId, Long companyId) {
        CleanerInfo resource = new CleanerInfo();
        try {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                return resource;
            }

            File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
            resource.toClean.add(resultDir);

            File zipDir = new File(resultDir, "zip");
            //noinspection ResultOfMethodCallIgnored
            zipDir.mkdir();

            SourceCodeImpl sc = sourceCodeCache.findById(execContext.sourceCodeId);
            if (sc==null) {
                final String es = "#705.300 SourceCode wasn't found, sourceCodeId: " + execContext.sourceCodeId;
                log.warn(es);
                return resource;
            }

            SimpleVariable variable = variableRepository.findByIdAsSimple(variableId);
            if (variable==null) {
                final String es = "#705.330 Can't find variable #"+variableId;
                log.warn(es);
                return resource;
            }

            ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
            ExecContextApiData.ExecContextVariableStates info = getExecContextVariableStates(execContext);
            String ext = info.tasks.stream()
                    .filter(o->o.outputs!=null)
                    .flatMap(o->o.outputs.stream())
                    .filter(o->o.id.equals(variableId) && !S.b(o.ext))
                    .findFirst().map(o->o.ext)
                    .orElse(".bin") ;

            String filename = S.f("variable-%s-%s%s", variableId, variable.variable, ext);

            File varFile = new File(resultDir, "variable-"+variableId+".bin");
            variableService.storeToFileWithTx(variable.id, varFile);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            // https://stackoverflow.com/questions/93551/how-to-encode-the-filename-parameter-of-content-disposition-header-in-http
            httpHeaders.setContentDisposition(ContentDisposition.parse(
                    "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())));
            resource.entity = new ResponseEntity<>(new FileSystemResource(varFile), RestUtils.getHeader(httpHeaders, varFile.length()), HttpStatus.OK);
            return resource;
        } catch (VariableDataNotFoundException e) {
            log.error("#705.350 Variable #{}, context: {}, {}", e.variableId, e.context, e.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        } catch (Throwable th) {
            log.error("#705.370 General error", th);
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }
}
