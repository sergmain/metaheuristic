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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final ProcessorCache processorCache;
    private final TaskService taskService;
    private final ProcessorTopLevelService processorTopLevelService;
    private final ExecContextService execContextService;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;

    private static final long FUNCTION_INFOS_TIMEOUT_REFRESH = TimeUnit.SECONDS.toMillis(5);
    private List<DispatcherCommParamsYaml.Functions.Info> functionInfosCache = new ArrayList<>();
    private long mills = System.currentTimeMillis();

    public void process(ProcessorCommParamsYaml scpy, DispatcherCommParamsYaml lcpy) {
        lcpy.resendTaskOutputResource = checkForMissingOutputResources(scpy);
        processProcessorTaskStatus(scpy);
        processResendTaskOutputResourceResult(scpy);
        processProcessorTaskStatus(scpy);
        lcpy.reportResultDelivering = processReportTaskProcessingResult(scpy);
        processReportProcessorStatus(scpy);
        lcpy.assignedTask = processRequestTask(scpy);
        lcpy.assignedProcessorId = getNewProcessorId(scpy.requestProcessorId);
        lcpy.functions.infos.addAll( getFunctionInfos() );
    }

    private synchronized List<DispatcherCommParamsYaml.Functions.Info> getFunctionInfos() {
        if (System.currentTimeMillis() - mills > FUNCTION_INFOS_TIMEOUT_REFRESH) {
            mills = System.currentTimeMillis();
            final List<Long> allIds = functionRepository.findAllIds();
            functionInfosCache = allIds.stream()
                    .map(functionCache::findById)
                    .filter(Objects::nonNull)
                    .map(s->new DispatcherCommParamsYaml.Functions.Info(s.code, s.getFunctionConfig(false).sourcing))
                    .collect(Collectors.toList());
        }
        return functionInfosCache;
    }

    // processing at dispatcher side
    public DispatcherCommParamsYaml.ResendTaskOutputResource checkForMissingOutputResources(ProcessorCommParamsYaml request) {
        if (request.checkForMissingOutputResources==null) {
            return null;
        }
        final long processorId = Long.parseLong(request.processorCommContext.processorId);
        List<Long> ids = taskService.resourceReceivingChecker(processorId);
        return new DispatcherCommParamsYaml.ResendTaskOutputResource(ids);
    }

    // processing at dispatcher side
    public void processResendTaskOutputResourceResult(ProcessorCommParamsYaml request) {
        if (request.resendTaskOutputResourceResult==null) {
            return;
        }
        for (ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus status : request.resendTaskOutputResourceResult.statuses) {
            taskService.processResendTaskOutputResourceResult(request.processorCommContext.processorId, status.status, status.taskId);
        }
    }

    // processing at dispatcher side
    public void processProcessorTaskStatus(ProcessorCommParamsYaml request) {
        if (request.reportProcessorTaskStatus ==null || request.reportProcessorTaskStatus.statuses==null) {
            return;
        }
        processorTopLevelService.reconcileProcessorTasks(request.processorCommContext.processorId, request.reportProcessorTaskStatus.statuses);
    }

    // processing at dispatcher side
    public DispatcherCommParamsYaml.ReportResultDelivering processReportTaskProcessingResult(ProcessorCommParamsYaml request) {
        if (request.reportTaskProcessingResult==null || request.reportTaskProcessingResult.results==null) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final DispatcherCommParamsYaml.ReportResultDelivering cmd1 = new DispatcherCommParamsYaml.ReportResultDelivering(
                execContextService.storeAllConsoleResults(request.reportTaskProcessingResult.results)
        );
        return cmd1;
    }

    // processing at dispatcher side
    public void processReportProcessorStatus(ProcessorCommParamsYaml request) {
        if (request.reportProcessorStatus ==null) {
            return;
        }
        checkProcessorId(request);
        processorTopLevelService.storeProcessorStatuses(request.processorCommContext.processorId, request.reportProcessorStatus, request.functionDownloadStatus);
    }

    // processing at dispatcher side
    public DispatcherCommParamsYaml.AssignedTask processRequestTask(ProcessorCommParamsYaml request) {
        if (request.requestTask==null) {
            return null;
        }
        checkProcessorId(request);
        DispatcherCommParamsYaml.AssignedTask assignedTask =
                execContextService.getTaskAndAssignToProcessor(Long.parseLong(request.processorCommContext.processorId), request.requestTask.isAcceptOnlySigned(), null);

        if (assignedTask!=null) {
            log.info("Assign task #{} to processor #{}", assignedTask.getTaskId(), request.processorCommContext.processorId);
        }
        return assignedTask;
    }

    public void checkProcessorId(ProcessorCommParamsYaml request) {
        if (request.processorCommContext ==null  || request.processorCommContext.processorId ==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("processorId is null");
        }
    }

    // processing at dispatcher side
    public DispatcherCommParamsYaml.AssignedProcessorId getNewProcessorId(ProcessorCommParamsYaml.RequestProcessorId request) {
        if (request==null) {
            return null;
        }
        String sessionId = ProcessorTopLevelService.createNewSessionId();
        final Processor st = new Processor();
        ProcessorStatusYaml ss = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown);

        st.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        processorCache.save(st);

        // TODO 2019.05.19 why do we send processorId as a String?
        return new DispatcherCommParamsYaml.AssignedProcessorId(Long.toString(st.getId()), sessionId);
    }
}
