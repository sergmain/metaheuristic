/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 8:03 PM
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor
public class ProcessorCommandProcessor {
    private final ProcessorService processorService;
    private final MetadataService metadataService;

    // this method is synchronized outside
    public void processDispatcherCommParamsYaml(ProcessorCommParamsYaml pcpy, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml dispatcherYaml) {

        pcpy.resendTaskOutputResourceResult = resendTaskOutputResource(processorCode, dispatcherUrl, dispatcherYaml);
        processReportResultDelivering(processorCode, dispatcherUrl, dispatcherYaml);
        processAssignedTask(processorCode, dispatcherUrl, dispatcherYaml);
        storeProcessorId(processorCode, dispatcherUrl, dispatcherYaml);
        reAssignProcessorId(processorCode, dispatcherUrl, dispatcherYaml);
    }

    // processing at processor side
    @Nullable
    private ProcessorCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResource(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.resendTaskOutputs==null || request.resendTaskOutputs.resends.isEmpty()) {
            return null;
        }
        List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
        for (DispatcherCommParamsYaml.ResendTaskOutput output : request.resendTaskOutputs.resends) {
            Enums.ResendTaskOutputResourceStatus status = processorService.resendTaskOutputResources(processorCode, dispatcherUrl, output.taskId, output.variableId);
            statuses.add( new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(output.taskId, output.variableId, status));
        }
        return new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses);
    }

    // processing at processor side
    private void processReportResultDelivering(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.reportResultDelivering==null) {
            return;
        }
        processorService.markAsDelivered(processorCode, dispatcherUrl, request.reportResultDelivering.getIds());
    }

    private void processAssignedTask(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.assignedTask==null) {
            return;
        }
        processorService.assignTasks(processorCode, dispatcherUrl, request.assignedTask);
    }

    // processing at processor side
    private void storeProcessorId(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", request.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(processorCode,
                dispatcherUrl, request.assignedProcessorId.assignedProcessorId, request.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(String processorCode, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml request) {
        if (request.reAssignedProcessorId ==null) {
            return;
        }
        final String currProcessorId = metadataService.getProcessorId(processorCode, dispatcherUrl);
        final String currSessionId = metadataService.getSessionId(processorCode, dispatcherUrl);
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
        metadataService.setProcessorIdAndSessionId(processorCode,
                dispatcherUrl, request.reAssignedProcessorId.getReAssignedProcessorId(), request.reAssignedProcessorId.sessionId);
    }

}
