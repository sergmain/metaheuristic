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
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
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
        ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = metadataService.getRef(dispatcherUrl);
        if(ref==null) {
            log.warn("ref is null for processorId: {}, dispatcherUrl: {}",
                    pcpy.request.processorCommContext!=null ? pcpy.request.processorCommContext.processorId : "<null>", dispatcherUrl);
            return;
        }

        ProcessorCommParamsYaml.ProcessorRequest request = pcpy.request;
        DispatcherCommParamsYaml.DispatcherResponse response = dispatcherYaml.response;

        storeProcessorId(ref.dispatcherUrl, response);

        request.resendTaskOutputResourceResult = resendTaskOutputResource(ref, response);
        processReportResultDelivering(ref, response);
        reAssignProcessorId(ref.dispatcherUrl, response);

        for (ProcessorCommParamsYaml.Core core : request.cores) {
            for (DispatcherCommParamsYaml.Core c : response.cores) {
                if (!core.code.equals(c.code)) {
                    continue;
                }
                processAssignedTask(ref, response);
            }
        }
    }

    // processing at processor side
    private ProcessorCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResource(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, DispatcherCommParamsYaml.DispatcherResponse response) {
        List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = processorService.getResendTaskOutputResourceResultStatus(ref, response);
        return new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses);
    }

    // processing at processor side
    private void processReportResultDelivering(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.reportResultDelivering==null) {
            return;
        }
        processorService.markAsDelivered(core, response.reportResultDelivering.getIds());
    }

    private void processAssignedTask(ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core, DispatcherCommParamsYaml.DispatcherResponse response) {
        for (DispatcherCommParamsYaml.Core c : response.cores) {
            if (c.assignedTask==null) {
                continue;
            }
            processorService.assignTasks(core, c.assignedTask);
        }
    }

    // processing at processor side
    private void storeProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", response.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(dispatcherUrl, response.assignedProcessorId.assignedProcessorId, response.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml.DispatcherResponse response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        Long newProcessorId = Long.parseLong(response.reAssignedProcessorId.reAssignedProcessorId);
        final MetadataParamsYaml.ProcessorSession processorSession = metadataService.getProcessorSession(dispatcherUrl);
        final Long currProcessorId = processorSession.processorId;
        final String currSessionId = processorSession.sessionId;
        if (currProcessorId!=null && currSessionId!=null &&
                currProcessorId.equals(newProcessorId) && currSessionId.equals(response.reAssignedProcessorId.sessionId)
        ) {
            return;
        }

        log.info("""
            reAssignProcessorId(),
            \t\tcurrent processorId: {}, sessionId: {}
            \t\tnew processorId: {}, sessionId: {}""",
                currProcessorId, currSessionId,
                response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId
        );
        metadataService.setProcessorIdAndSessionId(dispatcherUrl, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
