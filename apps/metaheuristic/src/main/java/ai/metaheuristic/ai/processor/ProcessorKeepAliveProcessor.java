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

import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

    public void processKeepAliveResponseParamYaml(KeepAliveRequestParamYaml karpy, String dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        processExecContextStatus(dispatcherUrl, responseParamYaml.execContextStatus);
        storeProcessorId(dispatcherUrl, responseParamYaml);
        reAssignProcessorId(dispatcherUrl, responseParamYaml);
        registerFunctions(karpy.functions, dispatcherUrl, responseParamYaml);
//        processRequestLogFile(pcpy)
    }

    private void registerFunctions(KeepAliveRequestParamYaml.FunctionDownloadStatus functionDownloadStatus, String dispatcherUrl, KeepAliveResponseParamYaml dispatcherYaml) {
        List<FunctionDownloadStatusYaml.Status> statuses = metadataService.registerNewFunctionCode(dispatcherUrl, dispatcherYaml.functions.infos);
        for (FunctionDownloadStatusYaml.Status status : statuses) {
            functionDownloadStatus.statuses.add(new KeepAliveRequestParamYaml.FunctionDownloadStatus.Status(status.functionState, status.code));
        }
    }

    private void processExecContextStatus(String dispatcherUrl, KeepAliveResponseParamYaml.ExecContextStatus execContextStatus) {
        currentExecState.register(dispatcherUrl, execContextStatus.statuses);
    }

    // processing at processor side
    private void storeProcessorId(String dispatcherUrl, KeepAliveResponseParamYaml request) {
        if (request.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", request.assignedProcessorId);
        metadataService.setProcessorIdAndSessionId(
                dispatcherUrl, request.assignedProcessorId.assignedProcessorId.toString(), request.assignedProcessorId.assignedSessionId);
    }

    // processing at processor side
    private void reAssignProcessorId(String dispatcherUrl, KeepAliveResponseParamYaml response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        final String currProcessorId = metadataService.getProcessorId(dispatcherUrl);
        final String currSessionId = metadataService.getSessionId(dispatcherUrl);
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
                dispatcherUrl, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
