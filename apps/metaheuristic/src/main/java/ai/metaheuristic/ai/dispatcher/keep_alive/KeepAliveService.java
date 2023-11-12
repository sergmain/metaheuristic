/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.events.CheckProcessorIdEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorSyncService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreCache;
import ai.metaheuristic.ai.dispatcher.processor_core.ProcessorCoreTxService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Serge
 * Date: 11/22/2020
 * Time: 12:24 AM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class KeepAliveService {

    public static final int MAX_REQUEST_PROCESSING_TIME = 12_000;

    private final Globals globals;
    private final ProcessorTopLevelService processorTopLevelService;
    private final FunctionService functionTopLevelService;
    private final ProcessorCache processorCache;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final ProcessorTxService processorTxService;
    private final ProcessorCoreTxService processorCoreTxService;
    private final ProcessorCoreCache processorCoreCache;
    private final ExecContextStatusService execContextStatusService;

    private ThreadedPool<Long, CheckProcessorIdEvent> checkProcessorIdEventPool;

    @PostConstruct
    public void init() {
        this.checkProcessorIdEventPool = new ThreadedPool<>("CheckProcessorIdEvent-", 100, true,
                this::checkProcessorIdSynced, ConstsApi.DURATION_NONE);
    }

    private void initDispatcherInfo(KeepAliveResponseParamYaml keepAliveResponse) {
        keepAliveResponse.functions.infos.putAll(functionTopLevelService.toMapOfFunctionInfos());
        keepAliveResponse.execContextStatus = execContextStatusService.toExecContextStatus();
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
            // System.currentTimeMillis() - startMills < 12_000 - this is for to be sure that
            // request will be processed within http timeout window, which is 20 seconds
            if (System.currentTimeMillis() - startMills < MAX_REQUEST_PROCESSING_TIME || globals.isTesting()) {
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
            log.error("446.040 Error while processing client's request, size: {}, ProcessorCommParamsYaml:\n{}", json.length(), json);
            log.error("446.060 Error", th);
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
            log.warn("446.100 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            // no need of syncing for creation of new Processor
            DispatcherApiData.ProcessorSessionId processorSessionId = processorTxService.reassignProcessorId(remoteAddress, "Id was reassigned from " + processorRequest.processorCommContext.processorId);
            dispatcherResponse.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
            return processorSessionId.processorId;
        }

        Enums.ProcessorAndSessionStatus processorAndSessionStatus = ProcessorTopLevelService.checkProcessorAndSessionStatus(processor, processorRequest.processorCommContext.sessionId);
        if (processorAndSessionStatus != Enums.ProcessorAndSessionStatus.ok) {

            DispatcherApiData.ProcessorSessionId processorSessionId = null;
            CheckProcessorIdEvent event = new CheckProcessorIdEvent(processor.id, processorAndSessionStatus, remoteAddress);
            if (processorAndSessionStatus==Enums.ProcessorAndSessionStatus.updateSession) {
                checkProcessorIdEventPool.putToQueue(event);
            }
            else {
                processorSessionId = checkProcessorIdSynced(event);
            }

            if (processorSessionId != null) {
                dispatcherResponse.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
                return processorSessionId.processorId;
            }
        }
        log.debug("Start processing commands");
        processorTopLevelService.processKeepAliveData(processorRequest, req.functions, processor);

        //      keepAliveCommandProcessor.processLogRequest(processorRequest.processorCommContext.processorId, dispatcherResponse);

        return processorRequest.processorCommContext.processorId;
    }

    private DispatcherApiData.ProcessorSessionId checkProcessorIdSynced(CheckProcessorIdEvent event) {
        return ProcessorSyncService.getWithSync(event.processorId(),
            () -> processorTxService.checkProcessorId(event.processorAndSessionStatus(), event.processorId(), event.remoteAddress()));
    }

    private void processInfoAboutCores(Long processorId, KeepAliveRequestParamYaml req, long startMills, KeepAliveResponseParamYaml resp) {
        for (KeepAliveRequestParamYaml.Core core : req.cores) {
            if (core.coreId == null) {
                resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(processorCoreTxService.createProcessorCore(processorId, core).id, core.coreCode));
                continue;
            }

            final ProcessorCore processorCore = processorCoreCache.findById(core.coreId);
            if (processorCore == null || !processorCore.processorId.equals(processorId)) {
                log.warn("446.140 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
                // no need of syncing for creation of new Processor
                resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(processorCoreTxService.createProcessorCore(processorId, core).id, core.coreCode));
                continue;
            }
            if (coreMetadataDifferent(core, processorCore.getCoreStatusYaml())) {
                processorCoreTxService.updateCore(processorCore, core);
            }

            resp.response.coreInfos.add(new KeepAliveResponseParamYaml.CoreInfo(core.coreId, core.coreCode));

            if (System.currentTimeMillis() - startMills > MAX_REQUEST_PROCESSING_TIME) {
                break;
            }
        }
    }

    public static boolean coreMetadataDifferent(KeepAliveRequestParamYaml.Core core, CoreStatusYaml coreStatusYaml) {
        return !Objects.equals(core.coreDir, coreStatusYaml.currDir) || !Objects.equals(core.tags, coreStatusYaml.tags);
    }


}

