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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
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
    private final ApplicationEventPublisher eventPublisher;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;
    private final ExecContextSyncService execContextSyncService;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDesc(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = getExecContextsOrderByCreatedOnDescResult(sourceCodeId, pageable, context);
        result.sourceCodeId = sourceCodeId;
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

    public static ExecContextApiData.ExecContextStateResult getExecContextStateResult(ExecContextApiData.RawExecContextStateResult raw) {

        ExecContextApiData.ExecContextStateResult r = new ExecContextApiData.ExecContextStateResult();
        r.sourceCodeId = raw.sourceCodeId;
        r.sourceCodeType = raw.sourceCodeType;
        r.sourceCodeUid = raw.sourceCodeUid;
        r.sourceCodeValid = raw.sourceCodeValid;

        Set<String> contexts = new HashSet<>();
        Map<String, List<TaskApiData.SimpleTaskInfo>> map = new HashMap<>();
        for (TaskApiData.SimpleTaskInfo info : raw.infos) {
            contexts.add(info.context);
            map.computeIfAbsent(info.context, (o) -> new ArrayList<>()).add(info);
        }
        r.header = raw.processCodes.stream().map(o -> new ExecContextApiData.ColumnHeader(o, o)).toArray(ExecContextApiData.ColumnHeader[]::new);
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

        for (TaskApiData.SimpleTaskInfo taskInfo : raw.infos) {
            for (int i = 0; i < r.lines.length; i++) {
                TaskApiData.SimpleTaskInfo simpleTaskInfo = null;
                List<TaskApiData.SimpleTaskInfo> tasksInContext = map.get(r.lines[i].context);
                for (TaskApiData.SimpleTaskInfo contextTaskInfo : tasksInContext) {
                    if (contextTaskInfo.taskId.equals(taskInfo.taskId)) {
                        simpleTaskInfo = contextTaskInfo;
                        break;
                    }
                }
                if (simpleTaskInfo == null) {
                    continue;
                }
                int j = findOrAssignCol(r.header, simpleTaskInfo.process);
                r.lines[i].cells[j] = new ExecContextApiData.StateCell(simpleTaskInfo.taskId, simpleTaskInfo.state, simpleTaskInfo.context);
            }
        }
        return r;
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
/*

        if (log.isDebugEnabled()) {
            log.debug("#462.010 save execContext, id: #{}, ver: {}, execContext: {}", execContext.id, execContext.version, execContext);
            try {
                throw new RuntimeException("execContext stacktrace");
            }
            catch(RuntimeException e) {
                log.debug("execContext stacktrace", e);
            }
        }
*/
        if (execContext.id==null) {
            final ExecContextImpl ec = execContextCache.save(execContext);
            return ec;
        }
        else if (!em.contains(execContext) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("Bean %s isn't managed by EntityManager", execContext));
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

    @Transactional(readOnly = true)
    public ExecContextApiData.RawExecContextStateResult getRawExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context) {
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.sourceCodeId = sourceCodeId;
        initInfoAboutSourceCode(sourceCodeId, result);

        List<TaskApiData.SimpleTaskInfo> infos = getSimpleTaskInfos(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            ExecContextApiData.RawExecContextStateResult resultWithError = new ExecContextApiData.RawExecContextStateResult("Can't find execContext for Id " + execContextId);
            return resultWithError;
        }
        List<String> processCodes = ExecContextProcessGraphService.getTopologyOfProcesses(ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(ec.params));
        return new ExecContextApiData.RawExecContextStateResult(
                sourceCodeId, infos, processCodes, result.sourceCodeType, result.sourceCodeUid, result.sourceCodeValid);
    }

    private List<TaskApiData.SimpleTaskInfo> getSimpleTaskInfos(Long execContextId) {
        List<TaskImpl> tasks = taskRepository.findByExecContextIdAsList(execContextId);
        return tasks.stream().map(o-> {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
            return new TaskApiData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
        }).collect(Collectors.toList());

/*
        try (Stream<TaskImpl> stream = taskRepository.findAllByExecContextIdAsStream(execContextId)) {
            return stream.map(o-> {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
                return new TaskApiData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
            }).collect(Collectors.toList());
        }
*/
    }

    private void initInfoAboutSourceCode(Long sourceCodeId, ExecContextApiData.ExecContextsResult result) {
        TxUtils.checkTxExists();
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

    @Transactional(readOnly = true)
    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        return result;
    }

    @Transactional
    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
        eventPublisher.publishEvent(new DispatcherInternalEvent.DeleteExperimentByExecContextIdEvent(execContextId));
        variableRepository.deleteByExecContextId(execContextId);
        ExecContext execContext = execContextCache.findById(execContextId);
        if (execContext != null) {
            // unlock sourceCode if this is the last execContext in the sourceCode
            List<Long> ids = execContextRepository.findIdsBySourceCodeId(execContext.getSourceCodeId());
            if (ids.size()==1) {
                if (ids.get(0).equals(execContextId)) {
                    if (execContext.getSourceCodeId() != null) {
                        eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, false));
                    }
                }
                else {
                    log.warn("#705.020 unexpected state, execContextId: {}, ids: {}, ", execContextId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#705.040 unexpected state, execContextId: {}, ids is empty", execContextId);
            }
            execContextCache.deleteById(execContextId);
        }
    }

    @Transactional(readOnly = true)
    public SourceCodeApiData.ExecContextForDeletion getExecContextExtendedForDeletion(Long execContextId, DispatcherContext context) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextForDeletion("#705.060 execContext wasn't found, execContextId: " + execContextId);
        }
        ExecContextParamsYaml ecpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        SourceCodeApiData.ExecContextForDeletion result = new SourceCodeApiData.ExecContextForDeletion(execContext.sourceCodeId, execContext.id, ecpy.sourceCodeUid, EnumsApi.ExecContextState.from(execContext.state));
        return result;
    }

    @Transactional
    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        eventPublisher.publishEvent( new DispatcherInternalEvent.ExecContextDeletionEvent(this, execContextId) );
        deleteExecContext(execContextId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#705.080 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }



}
