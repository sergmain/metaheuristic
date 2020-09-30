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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.*;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;

    private final VariableService variableService;
    private final ExecContextCache execContextCache;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final ApplicationEventPublisher eventPublisher;

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
//    private final TaskSyncService taskSyncService;

    public OperationStatusRest execContextTargetState(Long execContextId, ExecContextState execState, Long companyUniqueId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#705.180 execContext wasn't found, execContextId: " + execContextId);
        }

        if (execContext.state !=execState.code) {
            execContextFSM.toState(execContext.id, execState);
            eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, true));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void changeValidStatus(Long execContextId, boolean status) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        execContext.setValid(status);
        execContextCache.save(execContext);
    }

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        return result;
    }

    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
        eventPublisher.publishEvent(new DispatcherInternalEvent.DeleteExperimentByExecContextIdEvent(execContextId));
        variableService.deleteByExecContextId(execContextId);
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
                    log.warn("#705.600 unexpected state, execContextId: {}, ids: {}, ", execContextId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#705.320 unexpected state, execContextId: {}, ids is empty", execContextId);
            }
            execContextCache.deleteById(execContextId);
        }
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        ExecContextState execState = ExecContextState.from(state.toUpperCase());
        if (execState== ExecContextState.UNKNOWN) {
            return new OperationStatusRest(OperationStatus.ERROR, "#560.390 Unknown exec state, state: " + state);
        }
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        status = execContextTargetState(execContextId, execState, context.getCompanyId());
        return status;
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
            return new OperationStatusRest(OperationStatus.ERROR, "#560.400 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }


}
