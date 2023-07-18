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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MetaheuristicThreadLocal;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.dispatcher.event.events.TaskCommunicationEvent;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.keep_alive.KeepAliveTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.*;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
// !!! __ Do not change the name of class to SouthBridgeService ___ !!!
public class SouthbridgeService {

    private final Globals globals;
    private final VariableTxService variableService;
    private final GlobalVariableService globalVariableService;
    private final FunctionDataService functionDataService;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final KeepAliveTopLevelService keepAliveTopLevelService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProcessorCache processorCache;
    private final ProcessorTxService processorTransactionService;

    private static final CommonSync<String> commonSync = new CommonSync<>();

    private static void getWithSyncVoid(final EnumsApi.DataType binaryType, final String code, Runnable runnable) {
        TxUtils.checkTxNotExists();
        final String key = "--" + binaryType + "--" + code;
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(key);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public static <T> T getWithSyncNullable(final EnumsApi.DataType binaryType, final String code, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        final String key = "--" + binaryType + "--" + code;
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(key);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // return a requested data to a processor
    // data can be Function or Variable
    public CleanerInfo deliverData(@Nullable Long taskId, final EnumsApi.DataType binaryType, final String dataId, @Nullable final String chunkSize, final int chunkNum) {

        AssetFile assetFile;
        BiConsumer<String, Path> dataSaver;
        switch (binaryType) {
            case function:
                assetFile = AssetUtils.prepareFunctionFile(globals.dispatcherResourcesPath, dataId, null);
                if (assetFile.isError) {
                    String es = "#444.100 Function with id " + dataId + " is broken";
                    log.error(es);
                    throw new FunctionDataNotFoundException(dataId, es);
                }
                dataSaver = functionDataService::storeToFile;
                break;
            case variable:
                assetFile = AssetUtils.prepareFileForVariable(globals.dispatcherTempPath, "" + EnumsApi.DataType.variable + '-' + dataId, null, binaryType);
                if (assetFile.isError) {
                    String es = "#444.120 Resource with id " + dataId + " is broken";
                    log.error(es);
                    throw new VariableDataNotFoundException(Long.parseLong(dataId), EnumsApi.VariableContext.local, es);
                }
                dataSaver = (variableId, trgFile) -> variableService.storeToFileWithTx(Long.parseLong(variableId), trgFile);
                if (taskId!=null) {
                    eventPublisher.publishEvent(new TaskCommunicationEvent(taskId));
                }
                break;
            case global_variable:
                assetFile = AssetUtils.prepareFileForVariable(globals.dispatcherTempPath, "" + EnumsApi.DataType.global_variable + '-' + dataId, null, binaryType);
                if (assetFile.isError) {
                    String es = "#444.140 Global variable with id " + dataId + " is broken";
                    log.error(es);
                    throw new VariableDataNotFoundException(Long.parseLong(dataId), EnumsApi.VariableContext.local, es);
                }
                dataSaver = (variableId, trgFile) -> globalVariableService.storeToFileWithTx(Long.parseLong(variableId), trgFile);
                break;
            default:
                throw new IllegalStateException("#444.160 Unknown type of data: " + binaryType);
        }

        CleanerInfo resource = new CleanerInfo();

        if (!assetFile.isContent) {
            try {
                getWithSyncVoid(binaryType, dataId, () -> dataSaver.accept(dataId, assetFile.file));
            }
            catch(VariableIsNullException e) {
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, new HttpHeaders(), HttpStatus.NO_CONTENT);
                return resource;
            }
            catch (CommonErrorWithDataException e) {
                log.error("#444.180 Error store data to temp file, data doesn't exist in db, id " + dataId + ", file: " + assetFile.file);
                throw e;
            }
        }

        InputStream fis;
        try {
            fis = Files.newInputStream(assetFile.file);
            resource.inputStreams.add(fis);

            InputStream realInputStream = fis;

            boolean isLastChunk;
            long byteToRead = Files.size(assetFile.file);
            if (chunkSize == null || chunkSize.isBlank()) {
                isLastChunk = true;
            } else {
                final long size = Long.parseLong(chunkSize);
                final long offset = size * chunkNum;
                if (offset >= Files.size(assetFile.file)) {
                    MultiValueMap<String, String> headers = new HttpHeaders();
                    headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, "true");
                    headers.add(Consts.HEADER_MH_CHUNK_SIZE, "0");
                    resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, headers, HttpStatus.OK);
                    return resource;
                }
                final long realSize = Files.size(assetFile.file) < offset + size ? Files.size(assetFile.file) - offset : size;
                byteToRead = realSize;
                long skipped = fis.skip(offset);
                if (skipped!=offset) {
                    String es = S.f("#444.190 Error (skipped!=offset), skipped: %d, offset: %d", skipped, offset);
                    log.error(es);
                    throw new CommonIOErrorWithDataException(es);

                }
                realInputStream = new BoundedInputStream(fis, realSize);
                isLastChunk = (Files.size(assetFile.file) == (offset + realSize));
            }
            final HttpHeaders headers = RestUtils.getHeader(byteToRead);
            headers.add(Consts.HEADER_MH_CHUNK_SIZE, Long.toString(byteToRead));
            headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, Boolean.toString(isLastChunk));
            resource.entity = new ResponseEntity<>(new InputStreamResource(realInputStream), headers, HttpStatus.OK);
            return resource;
        } catch (IOException e) {
            throw new CommonIOErrorWithDataException("Error: " + e.getMessage());
        }
    }

    public String keepAlive(String data, String remoteAddress) {
        KeepAliveRequestParamYaml karpy = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.to(data);
        KeepAliveResponseParamYaml response = keepAliveTopLevelService.processKeepAliveInternal(karpy, remoteAddress, System.currentTimeMillis());
        String yaml = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.toString(response);
        log.info("#444.194 keepAlive(), size of request yaml: {}, response yaml: {}", data.length(), yaml.length());
        return yaml;
    }

    public String processRequest(String data, String remoteAddress) {
        ProcessorCommParamsYaml scpy = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        DispatcherCommParamsYaml lcpy = processRequestInternal(remoteAddress, scpy);
        String yaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        log.info("#444.196 processRequest(), size of yaml: {}", yaml.length());
        return yaml;
    }

    private DispatcherCommParamsYaml processRequestInternal(String remoteAddress, ProcessorCommParamsYaml scpy) {
        if (log.isDebugEnabled()) {
            MetaheuristicThreadLocal.getExecutionStat().setStat(true);
        }
        try {
            DispatcherCommParamsYaml lcpy = new DispatcherCommParamsYaml();
            processing(remoteAddress, scpy, lcpy);
            return lcpy;
        } catch (Throwable th) {
            String json;
            try {
                json = JsonUtils.getMapper().writeValueAsString(scpy);
            }
            catch (JsonProcessingException e) {
                json = scpy.toString();
            }
            log.error("#444.320 Error while processing client's request, size: {}, ProcessorCommParamsYaml:\n{}", json.length(), json);
            log.error("#444.330 Error", th);
            DispatcherCommParamsYaml lcpy = new DispatcherCommParamsYaml();
            lcpy.success = false;
            lcpy.msg = th.getMessage();
            return lcpy;
        }
        finally {
            if (log.isDebugEnabled()) {
                MetaheuristicThreadLocal.getExecutionStat().print().forEach(log::debug);
            }
            MetaheuristicThreadLocal.getExecutionStat().execStat.clear();
        }
    }

    private void processing(String remoteAddress, ProcessorCommParamsYaml scpy, DispatcherCommParamsYaml lcpy) {
        DispatcherData.TaskQuotas quotas = new DispatcherData.TaskQuotas(scpy.request.currentQuota);

        long startMills = System.currentTimeMillis();
        final boolean queueEmpty = MetaheuristicThreadLocal.getExecutionStat().get("findTask -> isQueueEmpty()",
                () -> TaskQueueSyncStaticService.getWithSync(TaskQueueService::isQueueEmpty));

        ProcessorCommParamsYaml.ProcessorRequest request = scpy.request;
        DispatcherCommParamsYaml.DispatcherResponse response = lcpy.response;

        if (request.processorCommContext == null || request.processorCommContext.processorId==null) {
            DispatcherApiData.ProcessorSessionId processorSessionId = dispatcherCommandProcessor.getNewProcessorId();
            response.assignedProcessorId = new DispatcherCommParamsYaml.AssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
            return;
        }

        Long processorId = request.processorCommContext.processorId;
        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.warn("#444.200 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            DispatcherApiData.ProcessorSessionId processorSessionId = processorTransactionService.reassignProcessorId(remoteAddress, "Id was reassigned from " + request.processorCommContext.processorId);
            response.reAssignedProcessorId = new DispatcherCommParamsYaml.ReAssignProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
            return;
        }

        Enums.ProcessorAndSessionStatus processorAndSessionStatus = ProcessorTopLevelService.checkProcessorAndSessionStatus(processor, request.processorCommContext.sessionId);
        if (processorAndSessionStatus == Enums.ProcessorAndSessionStatus.reassignProcessor || processorAndSessionStatus == Enums.ProcessorAndSessionStatus.newSession) {
            // do nothing because sessionId will be initialized via KeepAlive call
            log.info("#444.220 do nothing: (processorAndSessionStatus==Enums.ProcessorAndSessionStatus.reassignProcessor || processorAndSessionStatus== Enums.ProcessorAndSessionStatus.newSession)");
            return;
        }

        log.debug("Start processing commands");
        dispatcherCommandProcessor.process(request, response, startMills);
        if (System.currentTimeMillis() - startMills > Consts.DISPATCHER_REQUEST_PROCESSSING_MILLISECONDS && !globals.isTesting()) { return; }

        for (ProcessorCommParamsYaml.Core core : scpy.request.cores) {
            log.debug("Start processing commands");
            dispatcherCommandProcessor.processCores(core, request, response, quotas, queueEmpty);
            if (System.currentTimeMillis() - startMills > Consts.DISPATCHER_REQUEST_PROCESSSING_MILLISECONDS && !globals.isTesting()) {
                break;
            }
        }
    }

    private static boolean isProcessorContextNeedToBeChanged(DispatcherCommParamsYaml.DispatcherResponse lcpy) {
        return lcpy.reAssignedProcessorId !=null || lcpy.assignedProcessorId !=null;
    }

}
