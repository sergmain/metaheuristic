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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

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
        for (ProcessorCommParamsYaml.ProcessorRequest request : pcpy.requests) {
            for (DispatcherCommParamsYaml.DispatcherResponse response : dispatcherYaml.responses) {
                if (!request.processorCode.equals(response.processorCode)) {
                    continue;
                }

                ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = metadataService.getRef(request.processorCode, dispatcherUrl);
                if(ref==null) {
                    log.warn("ref is null for processorId: {}, dispatcherUrl: {}", request.processorCode, dispatcherUrl);
                    continue;
                }
                request.resendTaskOutputResourceResult = resendTaskOutputResource(dispatcherUrl, response);
                processReportResultDelivering(ref, response);
                processAssignedTask(ref, response);
                storeProcessorId(ref, response);
                reAssignProcessorId(ref, response);
            }
        }
    }

    // processing at processor side
    private ProcessorCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResource(DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml.DispatcherResponse response) {
        List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = processorService.getResendTaskOutputResourceResultStatus(dispatcherUrl, response);
        return new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses);
    }

    // processing at processor side
    private void processReportResultDelivering(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.reportResultDelivering==null) {
            return;
        }
        processorService.markAsDelivered(ref, response.reportResultDelivering.getIds());
    }

    private void processAssignedTask(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.assignedTask==null) {
            return;
        }
        processorService.assignTasks(ref, response.assignedTask);
    }

    // processing at processor side
    private void storeProcessorId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", response.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(ref, response.assignedProcessorId.assignedProcessorId, response.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        final String currProcessorId = metadataService.getProcessorId(ref);
        final String currSessionId = metadataService.getSessionId(ref);
        if (currProcessorId!=null && currSessionId!=null &&
                currProcessorId.equals(response.reAssignedProcessorId.getReAssignedProcessorId()) &&
                currSessionId.equals(response.reAssignedProcessorId.sessionId)
        ) {
            return;
        }

        log.info("reAssignProcessorId(),\n\t\tcurrent processorId: {}, sessionId: {}\n\t\t" +
                        "new processorId: {}, sessionId: {}",
                currProcessorId, currSessionId,
                response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId
        );
        metadataService.setProcessorIdAndSessionId(ref, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
