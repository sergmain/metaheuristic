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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.KeepAliveCommandProcessor;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.dispatcher.event.TaskCommunicationEvent;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.CommonIOErrorWithDataException;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
// !!! Do not change the name to SouthBridgeService
public class SouthbridgeService {

    private final Globals globals;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final FunctionDataService functionDataService;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final KeepAliveCommandProcessor keepAliveCommandProcessor;
    private final ProcessorTopLevelService processorTopLevelService;
    private final ApplicationEventPublisher eventPublisher;

    private static final CommonSync<String> commonSync = new CommonSync<>();

    private static <T> T getWithSync(final EnumsApi.DataType binaryType, final String code, Supplier<T> supplier) {
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
        BiFunction<String, File, Void> dataSaver;
        switch (binaryType) {
            case function:
                assetFile = AssetUtils.prepareFunctionFile(globals.dispatcherResourcesDir, dataId, null);
                if (assetFile.isError) {
                    String es = "#444.100 Function with id " + dataId + " is broken";
                    log.error(es);
                    throw new FunctionDataNotFoundException(dataId, es);
                }
                dataSaver = functionDataService::storeToFile;
                break;
            case variable:
                assetFile = AssetUtils.prepareFileForVariable(globals.dispatcherTempDir, ""+ EnumsApi.DataType.variable+'-'+dataId, null, binaryType);
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
                assetFile = AssetUtils.prepareFileForVariable(globals.dispatcherTempDir, ""+ EnumsApi.DataType.global_variable+'-'+dataId, null, binaryType);
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

        if (!assetFile.isContent) {
            try {
                getWithSync(binaryType, dataId, () -> dataSaver.apply(dataId, assetFile.file));
            } catch (CommonErrorWithDataException e) {
                log.error("#444.180 Error store data to temp file, data doesn't exist in db, id " + dataId + ", file: " + assetFile.file.getPath());
                throw e;
            }
        }
        FileInputStream fis;
        try {
            fis = new FileInputStream(assetFile.file);
            CleanerInfo resource = new CleanerInfo();
            resource.inputStreams.add(fis);

            InputStream realInputStream = fis;

            boolean isLastChunk;
            long byteToRead = assetFile.file.length();
            if (chunkSize == null || chunkSize.isBlank()) {
                isLastChunk = true;
            } else {
                final long size = Long.parseLong(chunkSize);
                final long offset = size * chunkNum;
                if (offset >= assetFile.file.length()) {
                    MultiValueMap<String, String> headers = new HttpHeaders();
                    headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, "true");
                    headers.add(Consts.HEADER_MH_CHUNK_SIZE, "0");
                    resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, headers, HttpStatus.OK);
                    return resource;
                }
                final long realSize = assetFile.file.length() < offset + size ? assetFile.file.length() - offset : size;
                byteToRead = realSize;
                long skipped = fis.skip(offset);
                if (skipped!=offset) {
                    String es = S.f("#444.190 Error (skipped!=offset), skipped: %d, offset: %d", skipped, offset);
                    log.error(es);
                    throw new CommonIOErrorWithDataException(es);

                }
                realInputStream = new BoundedInputStream(fis, realSize);
                isLastChunk = (assetFile.file.length() == (offset + realSize));
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
        KeepAliveResponseParamYaml response = processKeepAliveInternal(karpy, remoteAddress);
        String yaml = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.toString(response);
        return yaml;
    }

    private KeepAliveResponseParamYaml processKeepAliveInternal(KeepAliveRequestParamYaml req, String remoteAddress) {
        KeepAliveResponseParamYaml resp = new KeepAliveResponseParamYaml();
        try {
            for (KeepAliveRequestParamYaml.ProcessorRequest processorRequest : req.requests) {
                KeepAliveResponseParamYaml.DispatcherResponse dispatcherResponse = new KeepAliveResponseParamYaml.DispatcherResponse(processorRequest.processorCode);
                resp.responses.add(dispatcherResponse);

                if (processorRequest.processorCommContext == null) {
                    dispatcherResponse.assignedProcessorId = keepAliveCommandProcessor.getNewProcessorId(new KeepAliveRequestParamYaml.RequestProcessorId());
                    continue;
                }
                if (processorRequest.processorCommContext.processorId == null) {
                    log.warn("#444.240 StringUtils.isBlank(processorId), return RequestProcessorId()");
                    DispatcherApiData.ProcessorSessionId processorSessionId = dispatcherCommandProcessor.getNewProcessorId();
                    dispatcherResponse.assignedProcessorId = new KeepAliveResponseParamYaml.AssignedProcessorId(processorSessionId.processorId, processorSessionId.sessionId);
                    continue;
                }

                DispatcherApiData.ProcessorSessionId processorSessionId = checkForReAssigningProcessorSessionId(processorRequest.processorCommContext.processorId, processorRequest.processorCommContext.sessionId, remoteAddress);
                if (processorSessionId != null) {
                    dispatcherResponse.reAssignedProcessorId = new KeepAliveResponseParamYaml.ReAssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
                    continue;
                }

                log.debug("Start processing commands");
                keepAliveCommandProcessor.processProcessorTaskStatus(processorRequest);
                keepAliveCommandProcessor.processReportProcessorStatus(req.functions, processorRequest);
                keepAliveCommandProcessor.processGetNewProcessorId(processorRequest, dispatcherResponse);
                keepAliveCommandProcessor.processLogRequest(processorRequest.processorCommContext.processorId, dispatcherResponse);
            }
            keepAliveCommandProcessor.initDispatcherInfo(resp);

        } catch (Throwable th) {
            log.error("#444.220 Error while processing client's request, ProcessorCommParamsYaml:\n{}", req);
            log.error("#444.230 Error", th);
            resp.success = false;
            resp.msg = th.getMessage();
        }
        return resp;
    }

    public String processRequest(String data, String remoteAddress) {
        ProcessorCommParamsYaml scpy = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        DispatcherCommParamsYaml lcpy = processRequestInternal(remoteAddress, scpy);
        String yaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        return yaml;
    }

    private DispatcherCommParamsYaml processRequestInternal(String remoteAddress, ProcessorCommParamsYaml scpy) {
        DispatcherCommParamsYaml lcpy = new DispatcherCommParamsYaml();
        DispatcherData.TaskQuotas quotas = new DispatcherData.TaskQuotas(scpy.quotas.current);
        try {
            for (ProcessorCommParamsYaml.ProcessorRequest request : scpy.requests) {
                DispatcherCommParamsYaml.DispatcherResponse response = new DispatcherCommParamsYaml.DispatcherResponse(request.processorCode);
                lcpy.responses.add(response);

                if (request.processorCommContext ==null || S.b(request.processorCommContext.processorId)) {
                    DispatcherApiData.ProcessorSessionId processorSessionId = dispatcherCommandProcessor.getNewProcessorId();
                    response.assignedProcessorId = new DispatcherCommParamsYaml.AssignedProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
                    continue;
                }
                DispatcherApiData.ProcessorSessionId processorSessionId = checkForReAssigningProcessorSessionId(Long.parseLong(request.processorCommContext.processorId), request.processorCommContext.sessionId, remoteAddress);
                if (processorSessionId!=null) {
                    response.reAssignedProcessorId = new DispatcherCommParamsYaml.ReAssignProcessorId(processorSessionId.processorId.toString(), processorSessionId.sessionId);
                    continue;
                }

                log.debug("Start processing commands");
                dispatcherCommandProcessor.process(request, response, quotas);
            }
        } catch (Throwable th) {
            log.error("#444.220 Error while processing client's request, ProcessorCommParamsYaml:\n{}", scpy);
            log.error("#444.230 Error", th);
            lcpy.success = false;
            lcpy.msg = th.getMessage();
        }
        return lcpy;
    }

    private static boolean isProcessorContextNeedToBeChanged(DispatcherCommParamsYaml.DispatcherResponse lcpy) {
        return lcpy.reAssignedProcessorId !=null || lcpy.assignedProcessorId !=null;
    }

    @Nullable
    private DispatcherApiData.ProcessorSessionId checkForReAssigningProcessorSessionId(Long processorId, @Nullable String sessionId, String remoteAddress) {
        DispatcherApiData.ProcessorSessionId processorSessionId = processorTopLevelService.checkProcessorId(processorId, sessionId, remoteAddress);
        return processorSessionId;
    }
}
