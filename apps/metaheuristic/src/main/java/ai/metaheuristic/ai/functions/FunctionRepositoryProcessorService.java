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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionUtils;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 11/14/2023
 * Time: 11:20 PM
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionRepositoryProcessorService {

    public final ProcessorEnvironment processorEnvironment;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        public EnumsApi.FunctionState state;
        public String code;
        public String assetManagerUrl;
        public EnumsApi.FunctionSourcing sourcing;

        public EnumsApi.ChecksumState checksum = EnumsApi.ChecksumState.not_yet;
        public EnumsApi.SignatureState signature = EnumsApi.SignatureState.not_yet;

        public final Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public long lastCheck = 0;
    }

    @Data
    @AllArgsConstructor
    public static class FunctionConfigAndStatus {
        @Nullable
        public final TaskParamsYaml.FunctionConfig functionConfig;
        @Nullable
        public final Function status;
        @Nullable
        public final AssetFile assetFile;
        public final boolean contentIsInline;

        public FunctionConfigAndStatus(@Nullable Function status) {
            this.functionConfig = null;
            this.assetFile = null;
            this.contentIsInline = false;
            this.status = status;
        }

        public FunctionConfigAndStatus(@Nullable TaskParamsYaml.FunctionConfig functionConfig, @Nullable Function setFunctionState, AssetFile assetFile) {
            this.functionConfig = functionConfig;
            this.assetFile = assetFile;
            this.contentIsInline = false;
            this.status = setFunctionState;
        }
    }

    @Nullable
    public FunctionRepositoryRequestParams processFunctionRepositoryResponseParams(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, FunctionRepositoryResponseParams responseParams) {
        if (responseParams.functionCodes!=null) {
            for (String functionCode : responseParams.functionCodes) {

            }
        }
        return null;
    }


    public List<Function> registerNewFunctionCode(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, Map<EnumsApi.FunctionSourcing, String> infos) {
        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
            dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

        List<Pair<EnumsApi.FunctionSourcing, String>> list = new ArrayList<>();
        for (Map.Entry<EnumsApi.FunctionSourcing, String> e : infos.entrySet()) {
            String[] codes = e.getValue().split(",");
            for (String code : codes) {
                list.add(Pair.of(e.getKey(), code));
            }
        }

        try {
            writeLock.lock();
            boolean isChanged = false;
            for (Pair<EnumsApi.FunctionSourcing, String> info : list) {
                MetadataParamsYaml.Function status = metadata.functions.stream()
                    .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(info.getValue()))
                    .findFirst().orElse(null);
                if (status==null || status.state == EnumsApi.FunctionState.not_found) {
                    setFunctionDownloadStatusInternal(assetManagerUrl, info.getValue(), info.getKey(), EnumsApi.FunctionState.none);
                    isChanged = true;
                }
            }

            // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
            for (MetadataParamsYaml.Function status : metadata.functions) {
                if (status.assetManagerUrl.equals(assetManagerUrl.url) && list.stream().filter(i-> i.getValue().equals(status.code)).findFirst().orElse(null)==null) {
                    setFunctionDownloadStatusInternal(assetManagerUrl, status.code, status.sourcing, EnumsApi.FunctionState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        } finally {
            writeLock.unlock();
        }
        return metadata.functions;
    }

    @Nullable
    public MetadataParamsYaml.Function setFunctionState(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.240 functionCode is null");
        }
        try {
            writeLock.lock();
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return null;
            }
            status.state = functionState;
            if (status.state.needVerification) {
                status.checksum= EnumsApi.ChecksumState.not_yet;
                status.signature= EnumsApi.SignatureState.not_yet;
            }
            status.lastCheck = System.currentTimeMillis();
            updateMetadataFile();
            return status;
        } finally {
            writeLock.unlock();
        }
    }

    public void setFunctionFromProcessorAsReady(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        try {
            writeLock.lock();
            if (S.b(functionCode)) {
                throw new IllegalStateException("815.260 functionCode is null");
            }
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.state = EnumsApi.FunctionState.ready;
            status.checksum= EnumsApi.ChecksumState.runtime;
            status.signature= EnumsApi.SignatureState.runtime;
            status.lastCheck = System.currentTimeMillis();

            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    public void setChecksumMap(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, @Nullable Map<EnumsApi.HashAlgo, String> checksumMap) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.280 functionCode is null");
        }
        if (checksumMap==null) {
            return;
        }

        try {
            writeLock.lock();
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.checksumMap.putAll(checksumMap);
            status.lastCheck = System.currentTimeMillis();
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    public void setChecksumAndSignatureStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.300 functionCode is null");
        }
        try {
            writeLock.lock();
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.checksum = checkSumAndSignatureStatus.checksum;
            status.signature = checkSumAndSignatureStatus.signature;
            status.lastCheck = System.currentTimeMillis();
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    private boolean removeFunction(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        return removeFunction(assetManagerUrl.url, functionCode);
    }

    private boolean removeFunction(final String assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.320 functionCode is empty");
        }
        try {
            writeLock.lock();
            List<MetadataParamsYaml.Function> statuses = metadata.functions.stream().filter(o->!(o.assetManagerUrl.equals(assetManagerUrl) && o.code.equals(functionCode))).collect(Collectors.toList());
            if (statuses.size()!= metadata.functions.size()) {
                metadata.functions.clear();
                metadata.functions.addAll(statuses);
                updateMetadataFile();
            }
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public void setFunctionDownloadStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.360 functionCode is empty");
        }
        try {
            writeLock.lock();
            setFunctionDownloadStatusInternal(assetManagerUrl, functionCode, sourcing, functionState);
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    // it could be null if this function was deleted
    @Nullable
    public MetadataParamsYaml.Function getFunctionDownloadStatuses(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        try {
            readLock.lock();
            return metadata.functions.stream()
                .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(functionCode))
                .findFirst().orElse(null);
        } finally {
            readLock.unlock();
        }
    }

    public Map<EnumsApi.FunctionState, String> getAsFunctionDownloadStatuses(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        final Map<EnumsApi.FunctionState, List<String>> map = new HashMap<>();
        try {
            readLock.lock();
            metadata.functions.stream()
                .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url))
                .forEach(o->map.computeIfAbsent(o.state, (k)->new ArrayList<>()).add(o.code));
        } finally {
            readLock.unlock();
        }

        final Map<EnumsApi.FunctionState, String> infos = new HashMap<>();
        for (Map.Entry<EnumsApi.FunctionState, List<String>> entry : map.entrySet()) {
            infos.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        return infos;
    }

    private void setFunctionDownloadStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String code, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        MetadataParamsYaml.Function status = metadata.functions.stream().filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(code)).findFirst().orElse(null);
        if (status == null) {
            status = new MetadataParamsYaml.Function(
                EnumsApi.FunctionState.none, code, assetManagerUrl.url, sourcing,
                EnumsApi.ChecksumState.not_yet, EnumsApi.SignatureState.not_yet, System.currentTimeMillis());
            metadata.functions.add(status);
        }
        status.state = functionState;
        status.lastCheck = System.currentTimeMillis();
    }

    public List<MetadataParamsYaml.Function> getStatuses() {
        try {
            readLock.lock();
            return Collections.unmodifiableList(metadata.functions);
        } finally {
            readLock.unlock();
        }
    }

    private void markAllAsUnverified() {
        List<MetadataParamsYaml.Function> forRemoving = new ArrayList<>();
        for (MetadataParamsYaml.Function status : metadata.functions) {
            if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                continue;
            }
            if (status.state == EnumsApi.FunctionState.not_found) {
                forRemoving.add(status);
            }
            status.state = EnumsApi.FunctionState.none;
            status.checksum = EnumsApi.ChecksumState.not_yet;
            status.signature = EnumsApi.SignatureState.not_yet;
        }
        for (MetadataParamsYaml.Function status : forRemoving) {
            removeFunction(status.assetManagerUrl, status.code);
        }
    }

    @Nullable
    public FunctionConfigAndStatus syncFunctionStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, final String functionCode) {
        try {
            return syncFunctionStatusInternal(assetManagerUrl, assetManager, functionCode);
        } catch (Throwable th) {
            log.error("815.080 Error in syncFunctionStatus()", th);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.io_error));
        }
    }

    @Nullable
    private FunctionConfigAndStatus syncFunctionStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {
        MetadataParamsYaml.Function status = getFunctionDownloadStatuses(assetManagerUrl, functionCode);

        if (status == null) {
            return null;
        }
        if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
            log.warn("815.090 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, assetManagerUrl.url);
            return null;
        }
        if (status.state == EnumsApi.FunctionState.ready) {
            if (status.checksum!= EnumsApi.ChecksumState.not_yet && status.signature!= EnumsApi.SignatureState.not_yet) {
                return new FunctionConfigAndStatus(status);
            }
            setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.none);
        }

        ProcessorFunctionUtils.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
            ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);

        if (downloadedFunctionConfigStatus.status==ProcessorFunctionUtils.ConfigStatus.error) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.function_config_error));
        }
        if (downloadedFunctionConfigStatus.status==ProcessorFunctionUtils.ConfigStatus.not_found) {
            removeFunction(assetManagerUrl, functionCode);
            return null;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;
        setChecksumMap(assetManagerUrl, functionCode, functionConfig.checksumMap);

        if (S.b(functionConfig.file)) {
            log.error("815.100 name of file for function {} is blank and content of function is blank too", functionCode);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.function_config_error));
        }

        Path baseFunctionDir = prepareBaseDir(assetManagerUrl);

        final AssetFile assetFile = AssetUtils.prepareFunctionFile(baseFunctionDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error));
        }
        if (!assetFile.isContent) {
            return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.none), assetFile);
        }

        return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ok), assetFile);
    }


}
