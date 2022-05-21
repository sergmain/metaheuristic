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

package ai.metaheuristic.ai.dispatcher.keep_alive;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorSyncService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreCache;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreService;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.api.data.DispatcherApiData;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
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
public class KeepAliveTopLevelService {

    private final Globals globals;
    private final ProcessorTopLevelService processorTopLevelService;
    private final FunctionTopLevelService functionTopLevelService;
    private final ProcessorCache processorCache;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final ProcessorTransactionService processorTransactionService;
    private final ProcessorCoreService processorCoreService;
    private final ProcessorCoreCache processorCoreCache;

    private void initDispatcherInfo(KeepAliveResponseParamYaml keepAliveResponse) {
        keepAliveResponse.functions.infos.addAll( functionTopLevelService.getFunctionInfos() );
        keepAliveResponse.dispatcherInfo = new KeepAliveResponseParamYaml.DispatcherInfo(globals.dispatcher.chunkSize.toBytes(), Consts.PROCESSOR_COMM_VERSION);
    }

//    public void processLogRequest(Long processorId, KeepAliveResponseParamYaml.DispatcherResponse dispatcherResponse) {
//        dispatcherResponse.requestLogFile = processorTopLevelService.processLogRequest(processorId);
//    }
//

    public KeepAliveResponseParamYaml processKeepAliveInternal(KeepAliveRequestParamYaml req, String remoteAddress, long startMills) {
        KeepAliveResponseParamYaml resp = new KeepAliveResponseParamYaml();
        resp.response.processorCode = req.processor.processorCode;
        try {
            Long processorId = processInfoAboutProcessor(req, remoteAddress, resp);
            if (System.currentTimeMillis() - startMills < 12_000 || globals.isTesting()) {
                processInfoAboutCores(processorId, req, startMills, resp);
            }
            initDispatcherInfo(resp);
        } catch (Throwable th) {
            String json;
            try {
                json = JsonUtils.getMapper().writeValueAsString(req);
            }
            catch (JsonProcessingException e) {
                json = req.toString();
            }
            log.error("#446.220 Error while processing client's request, size: {}, ProcessorCommParamsYaml:\n{}", json.length(), json);
            log.error("#446.230 Error", th);
            resp.success = false;
            resp.msg = th.getMessage();
        }
        return resp;
    }

    private Long processInfoAboutProcessor(KeepAliveRequestParamYaml req, String remoteAddress, KeepAliveResponseParamYaml resp) {
        KeepAliveRequestParamYaml.Processor processorRequest = req.processor;
        KeepAliveResponseParamYaml.DispatcherResponse dispatcherResponse = resp.response;

        if (processorRequest.processorCommContext == null || processorRequest.processorCommContext.processorId==null) {
            DispatcherApiData.ProcessorSessionId processorSessionId = dispatcherCommandProcessor.getNewProcessorId();
            dispatcherResponse.assignedProcessorId = new KeepAliveResponseParamYaml.AssignedProcessorId(processorSessionId.processorId, processorSessionId.sessionId);
            return dispatcherResponse.assignedProcessorId.getAssignedProcessorId();
        }

        final Processor processor = processorCache.findById(processorRequest.processorCommContext.processorId);
        if (processor == null) {
            log.warn("#446.140 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            // no need of syncing for creation of new Processor
            DispatcherApiData.ProcessorSessionId processorSessionId = processorTransactionService.reassignProcessorId(remoteAddress, "Id was reassigned from " + processorRequest.processorCommContext.processorId);
            dispatcherResponse.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
            return processorSessionId.processorId;
        }

        Enums.ProcessorAndSessionStatus processorAndSessionStatus = ProcessorTopLevelService.checkProcessorAndSessionStatus(processor, processorRequest.processorCommContext.sessionId);
        if (processorAndSessionStatus != Enums.ProcessorAndSessionStatus.ok) {
            DispatcherApiData.ProcessorSessionId processorSessionId = ProcessorSyncService.getWithSync(processor.id,
                    ()-> processorTransactionService.checkProcessorId(processorAndSessionStatus, processor.id, remoteAddress));

            if (processorSessionId != null) {
                dispatcherResponse.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
                return processorSessionId.processorId;
            }
        }
        log.debug("Start processing commands");
        processorTopLevelService.processKeepAliveData(processorRequest, req.functions, processor);
//                keepAliveCommandProcessor.processLogRequest(processorRequest.processorCommContext.processorId, dispatcherResponse);

        return processorRequest.processorCommContext.processorId;
    }

    private void processInfoAboutCores(Long processorId, KeepAliveRequestParamYaml req, long startMills, KeepAliveResponseParamYaml resp) {
        for (KeepAliveRequestParamYaml.Core core : req.cores) {
            if (core.coreId == null) {
                resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(processorCoreService.getNewProcessorCoreId(processorId), core.coreCode));
                continue;
            }

            final ProcessorCore processorCore = processorCoreCache.findById(core.coreId);
            if (processorCore == null || !processorCore.processorId.equals(processorId)) {
                log.warn("#446.140 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
                // no need of syncing for creation of new Processor
                resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(processorCoreService.getNewProcessorCoreId(processorId), core.coreCode));
                continue;
            }
            resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(core.coreId, core.coreCode));

            if (System.currentTimeMillis() - startMills > 12_000) {
                break;
            }
        }
    }


}

