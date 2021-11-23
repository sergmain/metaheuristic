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
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

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
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;

    public void processKeepAliveResponseParamYaml(KeepAliveRequestParamYaml karpy, DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        for (KeepAliveResponseParamYaml.DispatcherResponse response : responseParamYaml.responses) {

            processExecContextStatus(dispatcherUrl, responseParamYaml.execContextStatus);
            ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = metadataService.getRef(response.processorCode, dispatcherUrl);
            if(ref==null) {
                log.warn("ref is null for processorId: {}, dispatcherUrl: {}", response.processorCode, dispatcherUrl);
                continue;
            }
            storeProcessorId(ref, response);
            reAssignProcessorId(ref, response);
        }

        registerFunctions(dispatcherUrl, responseParamYaml.functions);

//        processRequestLogFile(pcpy)
    }

    private void registerFunctions(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.Functions functions) {
        metadataService.registerNewFunctionCode(dispatcherUrl, functions.infos);
    }

    private void processExecContextStatus(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.ExecContextStatus execContextStatus) {
        currentExecState.register(dispatcherUrl, execContextStatus.statuses);
    }

    // processing at processor side
    private void storeProcessorId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", response.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(
                ref, response.assignedProcessorId.assignedProcessorId.toString(), response.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        final String currProcessorId = metadataService.getProcessorId(ref.processorCode, ref.dispatcherUrl);
        final String currSessionId = metadataService.getSessionId(ref.processorCode, ref.dispatcherUrl);
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
        metadataService.setProcessorIdAndSessionId(
                ref, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
