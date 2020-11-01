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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
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
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
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
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
// !!! Do not change the name to SouthBridgeService
public class SouthbridgeService {

    // Processor's version for communicating with dispatcher
    private static final int PROCESSOR_COMM_VERSION = new ProcessorCommParamsYaml().version;

    private final Globals globals;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final FunctionDataService functionDataService;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final ProcessorTopLevelService processorTopLevelService;
    private final ExecContextStatusService execContextStatusService;

    private static final CommonSync<String> commonSync = new CommonSync<>();

    private static <T> T getWithSync(final EnumsApi.DataType binaryType, final String code, Supplier<T> function) {
        TxUtils.checkTxNotExists();
        final String key = "--" + binaryType + "--" + code;
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(key);
        try {
            lock.lock();
            return function.get();
        } finally {
            lock.unlock();
        }
    }

    // return a requested data to a processor
    // data can be Function or Variable
    public CleanerInfo deliverData(final EnumsApi.DataType binaryType, final String dataId, final String chunkSize, final int chunkNum) {
        return getWithSync(binaryType, dataId,
                () -> getAbstractDataResponseEntity(chunkSize, chunkNum, binaryType, dataId));
    }

    private CleanerInfo getAbstractDataResponseEntity(@Nullable String chunkSize, int chunkNum, EnumsApi.DataType binaryType, String dataId) {

        AssetFile assetFile;
        BiConsumer<String, File> dataSaver;
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
                dataSaver = (variableId, trgFile) -> variableService.storeToFile(Long.parseLong(variableId), trgFile);
                break;
            case global_variable:
                assetFile = AssetUtils.prepareFileForVariable(globals.dispatcherTempDir, ""+ EnumsApi.DataType.global_variable+'-'+dataId, null, binaryType);
                if (assetFile.isError) {
                    String es = "#444.140 Global variable with id " + dataId + " is broken";
                    log.error(es);
                    throw new VariableDataNotFoundException(Long.parseLong(dataId), EnumsApi.VariableContext.local, es);
                }
                dataSaver = (variableId, trgFile) -> globalVariableService.storeToFile(Long.parseLong(variableId), trgFile);
                break;
            default:
                throw new IllegalStateException("#444.160 Unknown type of data: " + binaryType);
        }

        if (!assetFile.isContent) {
            try {
                dataSaver.accept(dataId, assetFile.file);
            } catch (CommonErrorWithDataException e) {
                log.error("#444.180 Error store data to temp file, data doesn't exist in db, id " + dataId + ", file: " + assetFile.file.getPath());
                throw e;
            }
        }
        FileInputStream fis;
        try {
            fis = new FileInputStream(assetFile.file);
            CleanerInfo resource = new CleanerInfo();
            resource.inputStreams = List.of(fis);

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

    public String processRequest(String data, String remoteAddress) {
        ProcessorCommParamsYaml scpy = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        DispatcherCommParamsYaml lcpy = processRequestInternal(remoteAddress, scpy);
        String yaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        return yaml;
    }

    private DispatcherCommParamsYaml processRequestInternal(String remoteAddress, ProcessorCommParamsYaml scpy) {
        DispatcherCommParamsYaml lcpy = new DispatcherCommParamsYaml();
        try {
            if (scpy.processorCommContext ==null) {
                lcpy.assignedProcessorId = dispatcherCommandProcessor.getNewProcessorId(new ProcessorCommParamsYaml.RequestProcessorId());
                return lcpy;
            }
            checkProcessorId(scpy.processorCommContext.getProcessorId(), scpy.processorCommContext.getSessionId(), remoteAddress, lcpy);
            if (isProcessorContextNeedToBeChanged(lcpy)) {
                log.debug("isProcessorContextNeedToBeChanged is true, {}", lcpy);
                return lcpy;
            }

            lcpy.execContextStatus = execContextStatusService.getExecContextStatuses();

            log.debug("Start processing commands");
            dispatcherCommandProcessor.process(scpy, lcpy);
            setDispatcherCommContext(lcpy);
        } catch (Throwable th) {
            log.error("#444.220 Error while processing client's request, ProcessorCommParamsYaml:\n{}", scpy);
            log.error("#444.230 Error", th);
            lcpy.success = false;
            lcpy.msg = th.getMessage();
        }
        return lcpy;
    }

    private boolean isProcessorContextNeedToBeChanged(DispatcherCommParamsYaml lcpy) {
        return lcpy.reAssignedProcessorId !=null || lcpy.assignedProcessorId !=null;
    }

    private void setDispatcherCommContext(DispatcherCommParamsYaml lcpy) {
        DispatcherCommParamsYaml.DispatcherCommContext lcc = new DispatcherCommParamsYaml.DispatcherCommContext();
        lcc.chunkSize = globals.chunkSize;
        lcc.processorCommVersion = PROCESSOR_COMM_VERSION;
        lcpy.dispatcherCommContext = lcc;
    }

    private void checkProcessorId(@Nullable String processorIdAsStr, @Nullable String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        if (StringUtils.isBlank(processorIdAsStr)) {
            log.warn("#444.240 StringUtils.isBlank(processorId), return RequestProcessorId()");
            lcpy.assignedProcessorId = dispatcherCommandProcessor.getNewProcessorId(new ProcessorCommParamsYaml.RequestProcessorId());
            return;
        }

        final long processorId = Long.parseLong(processorIdAsStr);
        processorTopLevelService.checkProcessorId(processorId, sessionId, remoteAddress, lcpy);
    }

    private static DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus to(ExecContext execContext) {
        return new DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus(execContext.getId(), EnumsApi.ExecContextState.toState(execContext.getState()));
    }

}
