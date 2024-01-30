/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MetaheuristicThreadLocal;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.event.events.CheckForLostTaskEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 8:03 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DispatcherCommandProcessor {

    private final Globals globals;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final TaskService taskTopLevelService;
    private final ProcessorTxService processorService;
    private final TaskProviderTopLevelService taskProviderService;
    private final ApplicationEventPublisher eventPublisher;

    public void process(ProcessorCommParamsYaml.ProcessorRequest request, DispatcherCommParamsYaml.DispatcherResponse response, long startMills) {
        if (request.processorCommContext==null || request.processorCommContext.processorId==null || S.b(request.processorCommContext.sessionId)) {
            throw new IllegalStateException("997.040 (scpy.processorCommContext==null || S.b(scpy.processorCommContext.processorId) || S.b(scpy.processorCommContext.sessionId))");
        }
        MetaheuristicThreadLocal.getExecutionStat().exec("checkForMissingOutputResources()",
                ()-> response.resendTaskOutputs = checkForMissingOutputResources(request));
        if (System.currentTimeMillis() - startMills > Consts.DISPATCHER_REQUEST_PROCESSSING_MILLISECONDS && !globals.isTesting()) { return;}

        MetaheuristicThreadLocal.getExecutionStat().exec("processResendTaskOutputResourceResult()",
                ()-> processResendTaskOutputResourceResult(request));
        if (System.currentTimeMillis() - startMills > Consts.DISPATCHER_REQUEST_PROCESSSING_MILLISECONDS && !globals.isTesting()) { return;}

        MetaheuristicThreadLocal.getExecutionStat().exec("processReportTaskProcessingResult()",
                ()->response.reportResultDelivering = processReportTaskProcessingResult(request));
    }

    public void processCores(ProcessorCommParamsYaml.Core core, ProcessorCommParamsYaml.ProcessorRequest request, DispatcherCommParamsYaml.DispatcherResponse response, DispatcherData.TaskQuotas quotas, boolean queueEmpty) {
        if (request.processorCommContext==null || request.processorCommContext.processorId==null || S.b(request.processorCommContext.sessionId)) {
            throw new IllegalStateException("997.060 (scpy.processorCommContext==null || S.b(scpy.processorCommContext.processorId) || S.b(scpy.processorCommContext.sessionId))");
        }
        DispatcherCommParamsYaml.AssignedTask assignedTask = MetaheuristicThreadLocal.getExecutionStat().get("processRequestTask()",
                ()-> processRequestTask(core, request, quotas, queueEmpty));

        DispatcherCommParamsYaml.Core responseCore = new DispatcherCommParamsYaml.Core(core.code, core.coreId, assignedTask);
        response.cores.add(responseCore);
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.ResendTaskOutputs checkForMissingOutputResources(ProcessorCommParamsYaml.ProcessorRequest request) {
        if (request.checkForMissingOutputResources==null || request.processorCommContext==null || request.processorCommContext.processorId==null) {
            return null;
        }
        final long processorId = request.processorCommContext.processorId;
        DispatcherCommParamsYaml.ResendTaskOutputs outputs = taskTopLevelService.variableReceivingChecker(processorId);
        return outputs;
    }

    // processing at dispatcher side
    private void processResendTaskOutputResourceResult(ProcessorCommParamsYaml.ProcessorRequest request) {
        if (request.resendTaskOutputResourceResult==null) {
            return;
        }
        if (request.processorCommContext==null) {
            log.warn("997.080 (request.processorCommContext==null)");
            return;
        }
        for (ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus status : request.resendTaskOutputResourceResult.statuses) {
            taskTopLevelService.processResendTaskOutputResourceResult(request.processorCommContext.processorId, status.status, status.taskId, status.variableId);
        }
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.ReportResultDelivering processReportTaskProcessingResult(ProcessorCommParamsYaml.ProcessorRequest request) {
        if (request.reportTaskProcessingResult==null || request.reportTaskProcessingResult.results==null) {
            return null;
        }
        final DispatcherCommParamsYaml.ReportResultDelivering cmd1 = new DispatcherCommParamsYaml.ReportResultDelivering(
                execContextTopLevelService.storeAllConsoleResults(request.reportTaskProcessingResult.results)
        );
        return cmd1;
    }

    // processing at dispatcher side
    @Nullable
    private DispatcherCommParamsYaml.AssignedTask processRequestTask(ProcessorCommParamsYaml.Core core, ProcessorCommParamsYaml.ProcessorRequest request, DispatcherData.TaskQuotas quotas, boolean queueEmpty) {
        if (core.requestTask==null) {
            return null;
        }

        if (core.coreId==null || request.processorCommContext ==null  || request.processorCommContext.processorId==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("997.100 (core.coreId==null || request.processorCommContext ==null  || request.processorCommContext.processorId==null)");
        }

        DispatcherCommParamsYaml.AssignedTask assignedTask;
        List<Long> taskIds = S.b(core.requestTask.taskIds) ?
                List.of() :
                Arrays.stream(StringUtils.split(core.requestTask.taskIds, ", ")).map(Long::parseLong).collect(Collectors.toList());

        try {
            log.info("997.110 Start finding task for assigning to core #{}", core.coreId);
            assignedTask = taskProviderService.findTask(core.coreId, core.requestTask.isAcceptOnlySigned(), quotas, taskIds, queueEmpty);
            log.info("997.115 Result of finding task for core #{} is {}", core.coreId, assignedTask);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("997.120 ObjectOptimisticLockingFailureException", e);
            log.error("997.140 Lets try requesting a new task one more time");
            try {
                assignedTask = taskProviderService.findTask(core.coreId, core.requestTask.isAcceptOnlySigned(), quotas, taskIds, queueEmpty);
            } catch (ObjectOptimisticLockingFailureException e1) {
                log.error("997.160 ObjectOptimisticLockingFailureException again", e1);
                assignedTask = null;
            }
        }
        if (!taskIds.isEmpty()) {
            eventPublisher.publishEvent(new CheckForLostTaskEvent(core.coreId, taskIds, core.requestTask.taskIds == null ? "" : core.requestTask.taskIds));
        }

        if (assignedTask!=null) {
            log.info("997.180 Assign task #{} to processor #{}, core #{}", assignedTask.getTaskId(), request.processorCommContext.processorId, core.coreId);
        }
        return assignedTask;
    }

    // processing at dispatcher side
    public DispatcherApiData.ProcessorSessionId getNewProcessorId() {
        return processorService.getNewProcessorId();
    }
}
