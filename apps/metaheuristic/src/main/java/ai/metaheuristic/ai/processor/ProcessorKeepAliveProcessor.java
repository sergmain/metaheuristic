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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 11:17 AM
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor
public class ProcessorKeepAliveProcessor {
    private final ProcessorService processorService;
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;

    // this method is synchronized outside
    public void processDispatcherCommParamsYaml(KeepAliveRequestParamYaml karpy, String dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        karpy.resendTaskOutputResourceResult = resendTaskOutputResource(dispatcherUrl, responseParamYaml);
        // !!! processExecContextStatus() must be processed before calling processAssignedTask()
        processExecContextStatus(dispatcherUrl, responseParamYaml.execContextStatus);
        processReportResultDelivering(dispatcherUrl, responseParamYaml);
        processAssignedTask(dispatcherUrl, responseParamYaml);
        storeProcessorId(dispatcherUrl, responseParamYaml);
        reAssignProcessorId(dispatcherUrl, responseParamYaml);
        registerFunctions(karpy.functionDownloadStatus, dispatcherUrl, responseParamYaml);
//        processRequestLogFile(pcpy)
    }

    private void registerFunctions(ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus, String dispatcherUrl, DispatcherCommParamsYaml dispatcherYaml) {
        List<FunctionDownloadStatusYaml.Status> statuses = metadataService.registerNewFunctionCode(dispatcherUrl, dispatcherYaml.functions.infos);
        for (FunctionDownloadStatusYaml.Status status : statuses) {
            functionDownloadStatus.statuses.add(new ProcessorCommParamsYaml.FunctionDownloadStatus.Status(status.functionState, status.code));
        }
    }

    // processing at processor side
    @Nullable
    private ProcessorCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResource(String dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.resendTaskOutputs==null || request.resendTaskOutputs.resends.isEmpty()) {
            return null;
        }
        List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
        for (DispatcherCommParamsYaml.ResendTaskOutput output : request.resendTaskOutputs.resends) {
            Enums.ResendTaskOutputResourceStatus status = processorService.resendTaskOutputResources(dispatcherUrl, output.taskId, output.variableId);
            statuses.add( new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(output.taskId, output.variableId, status));
        }
        return new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses);
    }

    private void processExecContextStatus(String dispatcherUrl, DispatcherCommParamsYaml.ExecContextStatus execContextStatus) {
        if (execContextStatus ==null) {
            return;
        }
        currentExecState.register(dispatcherUrl, execContextStatus.statuses);
    }

    // processing at processor side
    private void processReportResultDelivering(String dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.reportResultDelivering==null) {
            return;
        }
        processorService.markAsDelivered(dispatcherUrl, request.reportResultDelivering.getIds());
    }

    private void processAssignedTask(String dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.assignedTask==null) {
            return;
        }
        processorService.assignTasks(dispatcherUrl, request.assignedTask);
    }

    // processing at processor side
    private void storeProcessorId(String dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", request.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(
                dispatcherUrl, request.assignedProcessorId.assignedProcessorId, request.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(String dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.reAssignedProcessorId ==null) {
            return;
        }
        final String currProcessorId = metadataService.getProcessorId(dispatcherUrl);
        final String currSessionId = metadataService.getSessionId(dispatcherUrl);
        if (currProcessorId!=null && currSessionId!=null &&
                currProcessorId.equals(request.reAssignedProcessorId.getReAssignedProcessorId()) &&
                currSessionId.equals(request.reAssignedProcessorId.sessionId)
        ) {
            return;
        }

        log.info("reAssignProcessorId(),\n\t\tcurrent processorId: {}, sessionId: {}\n\t\t" +
                        "new processorId: {}, sessionId: {}",
                currProcessorId, currSessionId,
                request.reAssignedProcessorId.getReAssignedProcessorId(), request.reAssignedProcessorId.sessionId
        );
        metadataService.setProcessorIdAndSessionId(
                dispatcherUrl, request.reAssignedProcessorId.getReAssignedProcessorId(), request.reAssignedProcessorId.sessionId);
    }

}
