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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTopLevelService {

    private final ProcessorSyncService processorSyncService;
    private final ProcessorTransactionService processorTransactionService;
    private final TaskRepository taskRepository;
//    private final ExecContextTopLevelService execContextTopLevelService;
    private final Globals globals;
    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextTaskResettingService execContextTaskResettingService;

    // Attention, this value must be greater than
    // ai.metaheuristic.ai.dispatcher.server.ServerService.SESSION_UPDATE_TIMEOUT
    // at least for 20 seconds
    // TODO 2020-12-23 20 seconds because ....
    public static final long PROCESSOR_TIMEOUT = TimeUnit.SECONDS.toMillis(140);

    public ProcessorData.ProcessorResult getProcessor(Long id) {
        Processor processor = processorCache.findById(id);
        if (processor==null) {
            return new ProcessorData.ProcessorResult("#808.040 Processor wasn't found for id #"+ id);
        }
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processor);
        return r;
    }

    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.updateDescription(processorId, desc));
    }

    public OperationStatusRest deleteProcessorById(Long processorId) {
        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.deleteProcessorById(processorId));
    }

    public void processProcessorStatuses(
            final Long processorId, @Nullable KeepAliveRequestParamYaml.ReportProcessor status, KeepAliveRequestParamYaml.FunctionDownloadStatuses functionDownloadStatus) {

        processorSyncService.getWithSyncVoid(processorId, ()-> processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus));
    }

    public void reconcileProcessorTasks(@Nullable String processorIdAsStr, List<Long> taskIds) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final Long processorId = Long.valueOf(processorIdAsStr);
        processorSyncService.getWithSyncVoid( processorId, ()-> reconcileProcessorTasks(processorId, taskIds));
    }

    @Nullable
    public DispatcherApiData.ProcessorSessionId checkProcessorId(final Long processorId, @Nullable String sessionId, String remoteAddress) {
        DispatcherApiData.ProcessorSessionId processorSessionId = processorSyncService.getWithSyncNullable( processorId,
                ()-> processorTransactionService.checkProcessorId(processorId, sessionId, remoteAddress));
        return processorSessionId;
    }

    public OperationStatusRest requestLogFile(final Long processorId) {
        return processorSyncService.getWithSync( processorId, ()-> processorTransactionService.requestLogFile(processorId));
    }

    public void setTaskIds(@Nullable Long processorId, @Nullable String taskIds) {
        if (processorId==null) {
            log.warn("#808.060 setTaskIds() was called with processorId==null");
            return;
        }
        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.setTaskIds(processorId, taskIds));
    }

    public void setLogFileReceived(final long processorId) {
        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.setLogFileReceived(processorId));
    }

    public ProcessorData.ProcessorsResult getProcessors(Pageable pageable) {
        TxUtils.checkTxNotExists();
        pageable = ControllerUtils.fixPageSize(globals.dispatcher.rowsLimit.processor, pageable);
        ProcessorData.ProcessorsResult result = new ProcessorData.ProcessorsResult();
        Slice<Long> ids = processorRepository.findAllByOrderByUpdatedOnDescId(pageable);
        List<ProcessorData.ProcessorStatus> ss = new ArrayList<>(pageable.getPageSize()+1);
        for (Long processorId : ids) {
            Processor processor = processorCache.findById(processorId);
            if (processor ==null) {
                continue;
            }
            ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

            String blacklistReason = processorBlacklisted(status);

            boolean isFunctionProblem = status.downloadStatuses.stream()
                    .anyMatch(s->s.functionState != Enums.FunctionState.none &&
                            s.functionState != Enums.FunctionState.ready &&
                            s.functionState != Enums.FunctionState.not_found &&
                            s.functionState != Enums.FunctionState.ok);

            ss.add(new ProcessorData.ProcessorStatus(
                    processor, System.currentTimeMillis() - processor.updatedOn < PROCESSOR_TIMEOUT,
                    isFunctionProblem,
                    blacklistReason!=null, blacklistReason,
                    processor.updatedOn,
                    (StringUtils.isNotBlank(status.ip) ? status.ip : Consts.UNKNOWN_INFO),
                    (StringUtils.isNotBlank(status.host) ? status.host : Consts.UNKNOWN_INFO)
            ));
        }
        result.items =  new SliceImpl<>(ss, pageable, ids.hasNext());
        return result;
    }

    @Nullable
    private static String processorBlacklisted(ProcessorStatusYaml status) {
        if (status.taskParamsVersion > TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            return "#808.080 Dispatcher is too old and can't communicate to this processor, needs to be upgraded";
        }
        return null;
    }

    private void reconcileProcessorTasks(final Long processorId, List<Long> taskIds) {
        TxUtils.checkTxNotExists();

        List<Object[]> tasks = taskRepository.findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(processorId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;
            Long execContextId = ((Number)obj[2]).longValue();

            if (assignedOn==null) {
                log.error("#808.190 Processor #{} has a task with assignedOn is null", processorId);
            }

            boolean isFound = taskIds.contains(taskId);
            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);

            // if Processor haven't reported back about this task in 90 seconds,
            // this task will be de-assigned from this Processor
            if (!isFound && isExpired) {
                log.info("#808.200 De-assign task #{} from processor #{}", taskId, processorId);
                log.info("\tstatuses: {}", taskIds);
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tassignedOn: {}, isFound: {}, is expired: {}", assignedOn, isFound, isExpired);
                OperationStatusRest result = execContextSyncService.getWithSync(execContextId, () -> execContextTaskResettingService.resetTaskWithTx(execContextId, taskId));
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#808.220 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
    }

    public ProcessorData.BulkOperations processProcessorBulkDeleteCommit(String processorIdsStr) {
        ProcessorData.BulkOperations bulkOperations = new ProcessorData.BulkOperations();
        String[] processorIds = StringUtils.split(processorIdsStr, ", ");
        for (String processorIdStr : processorIds) {
            Long processorId = Long.parseLong(processorIdStr);
            OperationStatusRest statusRest = processorSyncService.getWithSync(processorId, ()-> processorTransactionService.deleteProcessorById(processorId));
            bulkOperations.operations.add(new ProcessorData.BulkOperation(processorId, statusRest));
        }
        return bulkOperations;
    }

    @Nullable
    public KeepAliveResponseParamYaml.RequestLogFile processLogRequest(Long processorId) {
        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#807.100 Processor wasn't found for processorId: " + processorId);
        }
        ProcessorStatusYaml psy = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

        if (psy.log!=null && psy.log.logRequested) {
            if (psy.log.requestedOn==0) {
                throw new IllegalStateException("(psy.log.requestedOn==0)");
            }
            // we will send request for a log file constantly until it'll be received.
            // Double requests will be handled at the Processor side.
            return new KeepAliveResponseParamYaml.RequestLogFile(psy.log.requestedOn);
        }
        return null;
    }
}
