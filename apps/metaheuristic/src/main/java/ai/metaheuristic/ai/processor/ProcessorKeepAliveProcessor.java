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

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final ProcessorEnvironment processorEnvironment;
    private final CurrentExecState currentExecState;

    public void processKeepAliveResponseParamYaml(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
//        log.debug("#776.020 DispatcherCommParamsYaml:\n{}", responseParamYaml);

        storeDispatcherContext(dispatcherUrl, responseParamYaml);
        processExecContextStatus(dispatcherUrl, responseParamYaml.execContextStatus);
        registerFunctions(dispatcherUrl, responseParamYaml.functions);

        final KeepAliveResponseParamYaml.DispatcherResponse response = responseParamYaml.response;
        ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = processorEnvironment.metadataService.getRef(dispatcherUrl);

        if(ref==null) {
            log.warn("ref is null for processorId: {}, dispatcherUrl: {}", response.processorCode, dispatcherUrl);
            return;
        }
        storeProcessorId(dispatcherUrl, response);
        reAssignProcessorId(dispatcherUrl, response);

        storeProcessorCoreId(ref, responseParamYaml.response.coreInfos);

//        processRequestLogFile(pcpy)
    }

    private void storeDispatcherContext(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        if (responseParamYaml.dispatcherInfo ==null) {
            return;
        }
        DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        if (dispatcher==null) {
            return;
        }

        int maxVersionOfProcessor = responseParamYaml.dispatcherInfo.processorCommVersion != null
                ? responseParamYaml.dispatcherInfo.processorCommVersion
                : 1;
        DispatcherContextInfoHolder.put(dispatcherUrl, new DispatcherData.DispatcherContextInfo(responseParamYaml.dispatcherInfo.chunkSize, maxVersionOfProcessor));
    }

    private void registerFunctions(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.Functions functions) {
        processorEnvironment.metadataService.registerNewFunctionCode(dispatcherUrl, functions.infos);
    }

    private void processExecContextStatus(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.ExecContextStatus execContextStatus) {
        currentExecState.register(dispatcherUrl, execContextStatus.statuses);
    }

    // processing at processor side
    private void storeProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", response.assignedProcessorId);
        processorEnvironment.metadataService.setProcessorIdAndSessionId(
                dispatcherUrl, response.assignedProcessorId.assignedProcessorId.toString(), response.assignedProcessorId.assignedSessionId);
    }

    private void storeProcessorCoreId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, List<KeepAliveResponseParamYaml.CoreInfo> coreInfos) {
        for (KeepAliveResponseParamYaml.CoreInfo coreInfo : coreInfos) {
            processorEnvironment.metadataService.setCoreId(ref.dispatcherUrl, coreInfo.code, coreInfo.coreId);
        }
    }

    // processing at processor side
    private void reAssignProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        Long newProcessorId = Long.parseLong(response.reAssignedProcessorId.reAssignedProcessorId);
        final MetadataParamsYaml.ProcessorSession processorSession = processorEnvironment.metadataService.getProcessorSession(dispatcherUrl);
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
        processorEnvironment.metadataService.setProcessorIdAndSessionId(
                dispatcherUrl, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
