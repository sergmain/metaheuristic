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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 8:03 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class DispatcherCommandProcessor {

    private final TaskService taskService;
    private final ProcessorTopLevelService processorTopLevelService;
    private final FunctionService functionService;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ProcessorTransactionService processorService;
    private final TaskProviderService taskProviderService;

    public void process(ProcessorCommParamsYaml scpy, DispatcherCommParamsYaml lcpy) {
        lcpy.resendTaskOutputs = checkForMissingOutputResources(scpy);
        processProcessorTaskStatus(scpy);
        processResendTaskOutputResourceResult(scpy);
        lcpy.reportResultDelivering = processReportTaskProcessingResult(scpy);
        processReportProcessorStatus(scpy, lcpy);
        lcpy.assignedTask = processRequestTask(scpy);
        lcpy.assignedProcessorId = getNewProcessorId(scpy.requestProcessorId);
        lcpy.functions.infos.addAll( functionService.getFunctionInfos() );
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.ResendTaskOutputs checkForMissingOutputResources(ProcessorCommParamsYaml request) {
        if (request.checkForMissingOutputResources==null || request.processorCommContext==null || request.processorCommContext.processorId==null) {
            return null;
        }
        final long processorId = Long.parseLong(request.processorCommContext.processorId);
        DispatcherCommParamsYaml.ResendTaskOutputs outputs = taskService.variableReceivingChecker(processorId);
        return outputs;
    }

    // processing at dispatcher side
    private void processResendTaskOutputResourceResult(ProcessorCommParamsYaml request) {
        if (request.resendTaskOutputResourceResult==null) {
            return;
        }
        if (request.processorCommContext==null) {
            log.warn("#997.010 (request.processorCommContext==null)");
            return;
        }
        for (ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus status : request.resendTaskOutputResourceResult.statuses) {
            execContextTopLevelService.processResendTaskOutputResourceResult(request.processorCommContext.processorId, status.status, status.taskId, status.variableId);
        }
    }

    // processing at dispatcher side
    private void processProcessorTaskStatus(ProcessorCommParamsYaml request) {
        if (request.processorCommContext==null) {
            log.warn("#997.020 (request.processorCommContext==null)");
            return;
        }
        if (request.reportProcessorTaskStatus==null || CollectionUtils.isEmpty(request.reportProcessorTaskStatus.statuses)) {
            return;
        }
        processorTopLevelService.reconcileProcessorTasks(request.processorCommContext.processorId, request.reportProcessorTaskStatus.statuses);
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.ReportResultDelivering processReportTaskProcessingResult(ProcessorCommParamsYaml request) {
        if (request.reportTaskProcessingResult==null || request.reportTaskProcessingResult.results==null) {
            return null;
        }
        final DispatcherCommParamsYaml.ReportResultDelivering cmd1 = new DispatcherCommParamsYaml.ReportResultDelivering(
                execContextTopLevelService.storeAllConsoleResults(request.reportTaskProcessingResult.results)
        );
        return cmd1;
    }

    // processing at dispatcher side
    private void processReportProcessorStatus(ProcessorCommParamsYaml request, DispatcherCommParamsYaml lcpy) {
/*
        if (request.reportProcessorStatus==null) {
            return;
        }
        if (request.processorCommContext==null) {
            log.warn("#997.030 (request.processorCommContext==null)");
            return;
        }
*/
        checkProcessorId(request);
        // IDEA has become too lazy
        if (S.b(request.processorCommContext.processorId)) {
            log.warn("#997.030 (request.processorCommContext==null)");
            return;
        }
        final Long processorId = Long.valueOf(request.processorCommContext.processorId);
        processorTopLevelService.processProcessorStatuses(processorId, request.reportProcessorStatus, request.functionDownloadStatus, lcpy);
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.AssignedTask processRequestTask(ProcessorCommParamsYaml request) {
        if (request.requestTask==null || Boolean.FALSE.equals(request.requestTask.newTask) ||
                request.processorCommContext==null || S.b(request.processorCommContext.processorId)) {
            return null;
        }
        checkProcessorId(request);
        final ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus = request.reportProcessorTaskStatus!=null
                ? request.reportProcessorTaskStatus
                : new ProcessorCommParamsYaml.ReportProcessorTaskStatus();

        DispatcherCommParamsYaml.AssignedTask assignedTask;
        try {
            assignedTask = taskProviderService.findTask(reportProcessorTaskStatus, Long.parseLong(request.processorCommContext.processorId), request.requestTask.isAcceptOnlySigned());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#997.045 ObjectOptimisticLockingFailureException", e);
            log.error("#997.047 Lets try requesting a new task one more time");
            try {
                assignedTask = taskProviderService.findTask(reportProcessorTaskStatus, Long.parseLong(request.processorCommContext.processorId), request.requestTask.isAcceptOnlySigned());
            } catch (ObjectOptimisticLockingFailureException e1) {
                log.error("#997.048 ObjectOptimisticLockingFailureException again", e1);
                assignedTask = null;
            }
        }

        if (assignedTask!=null) {
            log.info("#997.050 Assign task #{} to processor #{}", assignedTask.getTaskId(), request.processorCommContext.processorId);
        }
        return assignedTask;
    }

    private void checkProcessorId(ProcessorCommParamsYaml request) {
        if (request.processorCommContext ==null  || S.b(request.processorCommContext.processorId)) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#997.070 processorId is null");
        }
    }

    // processing at dispatcher side
    @Nullable
    public DispatcherCommParamsYaml.AssignedProcessorId getNewProcessorId(@Nullable ProcessorCommParamsYaml.RequestProcessorId request) {
        if (request==null) {
            return null;
        }
        ProcessorData.ProcessorWithSessionId processorWithSessionId = processorService.getNewProcessorId();

        // TODO 2019.05.19 why do we send processorId as a String?
        return new DispatcherCommParamsYaml.AssignedProcessorId(Long.toString(processorWithSessionId.processor.id), processorWithSessionId.sessionId);
    }
}
