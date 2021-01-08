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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.api.data.DispatcherApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 11/22/2020
 * Time: 12:24 AM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class KeepAliveCommandProcessor  {

    private final Globals globals;
    private final ProcessorTopLevelService processorTopLevelService;
    private final FunctionService functionService;
    private final ProcessorTransactionService processorService;
    private final ExecContextStatusService execContextStatusService;

    public void initDispatcherInfo(KeepAliveResponseParamYaml keepAliveResponse) {
        keepAliveResponse.functions.infos.addAll( functionService.getFunctionInfos() );
        keepAliveResponse.execContextStatus = execContextStatusService.getExecContextStatuses();
        keepAliveResponse.dispatcherInfo = new KeepAliveResponseParamYaml.DispatcherInfo(globals.chunkSize, Consts.PROCESSOR_COMM_VERSION);
    }

    public void processLogRequest(Long processorId, KeepAliveResponseParamYaml.DispatcherResponse dispatcherResponse) {
        dispatcherResponse.requestLogFile = processorTopLevelService.processLogRequest(processorId);
    }

    public void processGetNewProcessorId(KeepAliveRequestParamYaml.ProcessorRequest processorRequest, KeepAliveResponseParamYaml.DispatcherResponse response) {
        response.assignedProcessorId = getNewProcessorId(processorRequest.requestProcessorId);
    }

    public void processProcessorTaskStatus(KeepAliveRequestParamYaml.ProcessorRequest processorRequest) {
        if (processorRequest.processorCommContext==null) {
            log.warn("#997.020 (request.processorCommContext==null)");
            return;
        }
        processorTopLevelService.setTaskIds(processorRequest.processorCommContext.processorId, processorRequest.taskIds);

        // TODO 2020-11-22 need to decide what to do with reconcileProcessorTasks() below
//        processorTopLevelService.reconcileProcessorTasks(request.processorCommContext.processorId, request.reportProcessorTaskStatus.statuses);
    }

    public void processReportProcessorStatus(
            KeepAliveRequestParamYaml.FunctionDownloadStatuses functions, KeepAliveRequestParamYaml.ProcessorRequest processorRequest) {

        checkProcessorId(processorRequest.processorCommContext);
        // ###idea### why?
        processorTopLevelService.processProcessorStatuses(
                processorRequest.processorCommContext.processorId, processorRequest.processor, functions);
    }

    private void checkProcessorId(@Nullable KeepAliveRequestParamYaml.ProcessorCommContext commContext) {
        if (commContext ==null || commContext.processorId==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#997.070 processorId is null");
        }
    }

    @Nullable
    public KeepAliveResponseParamYaml.AssignedProcessorId getNewProcessorId(@Nullable KeepAliveRequestParamYaml.RequestProcessorId request) {
        if (request==null) {
            return null;
        }
        DispatcherApiData.ProcessorSessionId processorSessionId = processorService.getNewProcessorId();
        return new KeepAliveResponseParamYaml.AssignedProcessorId(processorSessionId.processorId, processorSessionId.sessionId);
    }
}

