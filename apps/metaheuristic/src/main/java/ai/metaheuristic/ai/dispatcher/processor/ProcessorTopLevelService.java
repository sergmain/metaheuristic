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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
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

//    private final ProcessorSyncService processorSyncService;
    private final ProcessorTransactionService processorTransactionService;
    private final TaskRepository taskRepository;
    private final ExecContextTopLevelService execContextTopLevelService;

    public ProcessorData.ProcessorsResult getProcessors(Pageable pageable) {
        return processorTransactionService.getProcessors(pageable);
    }

    public ProcessorData.ProcessorResult getProcessor(Long id) {
        return processorTransactionService.getProcessor(id);
    }

    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        return processorTransactionService.updateDescription(processorId, desc);
//        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.updateDescription(processorId, desc));
    }

    public OperationStatusRest deleteProcessorById(Long processorId) {
        return processorTransactionService.deleteProcessorById(processorId);
//        return processorSyncService.getWithSync(processorId, ()-> processorTransactionService.deleteProcessorById(processorId));
    }

    public DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        return processorTransactionService.assignNewSessionId(processor, ss);
//        return processorSyncService.getWithSync(processor.id, ()-> processorTransactionService.assignNewSessionId(processor, ss));
    }

    public void storeProcessorStatuses(@Nullable String processorIdAsStr, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final Long processorId = Long.valueOf(processorIdAsStr);
        processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus);
//        processorSyncService.getWithSyncVoid(processorId, ()-> processorTransactionService.storeProcessorStatuses(processorId, status, functionDownloadStatus));
    }

    public void reconcileProcessorTasks(@Nullable String processorIdAsStr, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final long processorId = Long.parseLong(processorIdAsStr);
        reconcileProcessorTasks(processorId, statuses);
//        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.reconcileProcessorTasks(processorId, statuses));
    }

    public void checkProcessorId(final long processorId, @Nullable String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        processorTransactionService.checkProcessorId(processorId, sessionId, remoteAddress, lcpy);
//        processorSyncService.getWithSyncVoid( processorId, ()-> processorTransactionService.checkProcessorId(processorId, sessionId, remoteAddress, lcpy));
    }

    private Void reconcileProcessorTasks(final long processorId, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        TxUtils.checkTxNotExists();

        List<Object[]> tasks = taskRepository.findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(processorId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;
            Long execContextId = ((Number)obj[2]).longValue();

            if (assignedOn==null) {
                log.error("#807.190 Processor #{} has a task with assignedOn is null", processorId);
            }

            boolean isFound = statuses.stream().anyMatch(status -> status.taskId == taskId);
            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);

            // if Processor haven't reported back about this task in 90 seconds,
            // this task will be de-assigned from this Processor
            if (!isFound && isExpired) {
                log.info("#807.200 De-assign task #{} from processor #{}", taskId, processorId);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tassignedOn: {}, isFound: {}, is expired: {}", assignedOn, isFound, isExpired);
                OperationStatusRest result = execContextTopLevelService.resetTask(taskId);
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#807.220 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
        return null;
    }


}
