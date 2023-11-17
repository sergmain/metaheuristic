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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.processor.utils.ProcessorUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.*;
import java.util.function.BiFunction;
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

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;
    private final GitSourcingService gitSourcingService;

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

    private List<Function> functions = new ArrayList<>();

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
            processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

        List<Pair<EnumsApi.FunctionSourcing, String>> list = new ArrayList<>();
        for (Map.Entry<EnumsApi.FunctionSourcing, String> e : infos.entrySet()) {
            String[] codes = e.getValue().split(",");
            for (String code : codes) {
                list.add(Pair.of(e.getKey(), code));
            }
        }

        boolean isChanged = false;
        for (Pair<EnumsApi.FunctionSourcing, String> info : list) {
            Function status = functions.stream()
                .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(info.getValue()))
                .findFirst().orElse(null);
            if (status==null || status.state == EnumsApi.FunctionState.not_found) {
                setFunctionDownloadStatusInternal(assetManagerUrl, info.getValue(), info.getKey(), EnumsApi.FunctionState.none);
                isChanged = true;
            }
        }

        // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
        for (Function status : functions) {
            if (status.assetManagerUrl.equals(assetManagerUrl.url) && list.stream().filter(i-> i.getValue().equals(status.code)).findFirst().orElse(null)==null) {
                setFunctionDownloadStatusInternal(assetManagerUrl, status.code, status.sourcing, EnumsApi.FunctionState.not_found);
                isChanged = true;
            }
        }
        if (isChanged) {
//            updateMetadataFile();
        }
        return functions;
    }

    @Nullable
    public Function setFunctionState(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.240 functionCode is null");
        }
        Function status = functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return null;
        }
        status.state = functionState;
        if (status.state.needVerification) {
            status.checksum= EnumsApi.ChecksumState.not_yet;
            status.signature= EnumsApi.SignatureState.not_yet;
        }
        status.lastCheck = System.currentTimeMillis();
        return status;
    }

    public void setFunctionFromProcessorAsReady(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.260 functionCode is null");
        }
        Function status = functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.state = EnumsApi.FunctionState.ready;
        status.checksum= EnumsApi.ChecksumState.runtime;
        status.signature= EnumsApi.SignatureState.runtime;
        status.lastCheck = System.currentTimeMillis();

    }

    public void setChecksumMap(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, @Nullable Map<EnumsApi.HashAlgo, String> checksumMap) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.280 functionCode is null");
        }
        if (checksumMap==null) {
            return;
        }
        Function status = functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.checksumMap.putAll(checksumMap);
        status.lastCheck = System.currentTimeMillis();
    }

    public void setChecksumAndSignatureStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.300 functionCode is null");
        }
        Function status = functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.checksum = checkSumAndSignatureStatus.checksum;
        status.signature = checkSumAndSignatureStatus.signature;
        status.lastCheck = System.currentTimeMillis();
    }

    private boolean removeFunction(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        return removeFunction(assetManagerUrl.url, functionCode);
    }

    private boolean removeFunction(final String assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.320 functionCode is empty");
        }
        List<Function> statuses = functions.stream().filter(o->!(o.assetManagerUrl.equals(assetManagerUrl) && o.code.equals(functionCode))).collect(Collectors.toList());
        if (statuses.size()!= functions.size()) {
            functions.clear();
            functions.addAll(statuses);
        }
        return true;
    }

    public void setFunctionDownloadStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("815.360 functionCode is empty");
        }
        setFunctionDownloadStatusInternal(assetManagerUrl, functionCode, sourcing, functionState);
//        updateMetadataFile();
    }

    // it could be null if this function was deleted
    @Nullable
    public Function getFunctionDownloadStatuses(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        return functions.stream()
            .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(functionCode))
            .findFirst().orElse(null);
    }

    public Map<EnumsApi.FunctionState, String> getAsFunctionDownloadStatuses(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        final Map<EnumsApi.FunctionState, List<String>> map = new HashMap<>();
        functions.stream()
            .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url))
            .forEach(o->map.computeIfAbsent(o.state, (k)->new ArrayList<>()).add(o.code));

        final Map<EnumsApi.FunctionState, String> infos = new HashMap<>();
        for (Map.Entry<EnumsApi.FunctionState, List<String>> entry : map.entrySet()) {
            infos.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        return infos;
    }

    private void setFunctionDownloadStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String code, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        Function status = functions.stream().filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(code)).findFirst().orElse(null);
        if (status == null) {
            status = new Function(
                EnumsApi.FunctionState.none, code, assetManagerUrl.url, sourcing,
                EnumsApi.ChecksumState.not_yet, EnumsApi.SignatureState.not_yet, System.currentTimeMillis());
            functions.add(status);
        }
        status.state = functionState;
        status.lastCheck = System.currentTimeMillis();
    }

    public List<Function> getStatuses() {
        return Collections.unmodifiableList(functions);
    }

    private void markAllAsUnverified() {
        List<Function> forRemoving = new ArrayList<>();
        for (Function status : functions) {
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
        for (Function status : forRemoving) {
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
        Function status = getFunctionDownloadStatuses(assetManagerUrl, functionCode);

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

        Path baseFunctionDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);

        final AssetFile assetFile = AssetUtils.prepareFunctionFile(baseFunctionDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error));
        }
        if (!assetFile.isContent) {
            return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.none), assetFile);
        }

        return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ok), assetFile);
    }

    @SuppressWarnings({"WeakerAccess"})
    // TODO 2019.05.02 implement unit-test for this method
    public FunctionRepositoryData.FunctionPrepareResult prepareFunction(DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function) {
        try {
            final MetadataParams metadataParams = processorEnvironment.metadataParams;
            if (function.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
                return prepareWithSourcingAsDispatcher(assetManagerUrl, function, globals);
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.git) {
                return prepareWithSourcingAsGit(globals.processorResourcesPath, assetManagerUrl, function, metadataParams, gitSourcingService::prepareFunction);
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.processor) {
                return prepareWithSourcingAsProcessor(dispatcher, function);
            }
            throw new IllegalStateException("100.460 Shouldn't get there");
        } catch (Throwable th) {
            String es = "100.480 System error: " + th.getMessage();
            log.error(es, th);
            FunctionRepositoryData.FunctionPrepareResult functionPrepareResult = new FunctionRepositoryData.FunctionPrepareResult();
            functionPrepareResult.function = function;
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1000, es);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
    }

    private static FunctionRepositoryData.FunctionPrepareResult prepareWithSourcingAsProcessor(DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher, TaskParamsYaml.FunctionConfig function) {
        FunctionRepositoryData.FunctionPrepareResult functionPrepareResult = new FunctionRepositoryData.FunctionPrepareResult();
        functionPrepareResult.function = function;

        final FunctionApiData.SystemExecResult checksumAndSignature = verifyChecksumAndSignature(dispatcher.dispatcherLookup.signatureRequired, dispatcher.getPublicKey(), functionPrepareResult.function);
        if (!checksumAndSignature.isOk) {
            log.warn("100.500 Function {} has a wrong checksum/signature, error: {}", functionPrepareResult.function.code, checksumAndSignature.console);
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, checksumAndSignature.exitCode, checksumAndSignature.console);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
        }
        return functionPrepareResult;
    }

    private static FunctionRepositoryData.FunctionPrepareResult prepareWithSourcingAsGit(Path processorResourcesPath, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, MetadataParams metadataParams, BiFunction<Path, TaskParamsYaml.FunctionConfig, SystemProcessLauncher.ExecResult> gitSourcing) {
        FunctionRepositoryData.FunctionPrepareResult functionPrepareResult = new FunctionRepositoryData.FunctionPrepareResult();
        functionPrepareResult.function = function;

        if (S.b(functionPrepareResult.function.file)) {
            String s = S.f("100.520 Function %s has a blank file", functionPrepareResult.function.code);
            log.warn(s);
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1, s);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
        final Path resourceDir = MetadataParams.prepareBaseDir(processorResourcesPath, assetManagerUrl);
        log.info("Root dir for function: " + resourceDir);
        SystemProcessLauncher.ExecResult result = gitSourcing.apply(resourceDir, functionPrepareResult.function);
        if (!result.ok) {
            log.warn("100.540 Function {} has a permanent error, {}", functionPrepareResult.function.code, result.error);
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1, result.error);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
        if (result.functionDir==null) {
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -777, "result.functionDir is null");
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
        functionPrepareResult.functionAssetFile = new AssetFile();
        functionPrepareResult.functionAssetFile.file = result.functionDir.resolve(Objects.requireNonNull(functionPrepareResult.function.file));
        log.info("Function asset file: {}, exist: {}", functionPrepareResult.functionAssetFile.file.toAbsolutePath(), Files.exists(functionPrepareResult.functionAssetFile.file));
        return functionPrepareResult;
    }

    private FunctionRepositoryData.FunctionPrepareResult prepareWithSourcingAsDispatcher(
        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, Globals Globals
    ) {
        FunctionRepositoryData.FunctionPrepareResult functionPrepareResult = new FunctionRepositoryData.FunctionPrepareResult();
        functionPrepareResult.function = function;

        final Path baseResourceDir = MetadataParams.prepareBaseDir(Globals.processorResourcesPath, assetManagerUrl);
        functionPrepareResult.functionAssetFile = AssetUtils.prepareFunctionFile(baseResourceDir, functionPrepareResult.function.getCode(), functionPrepareResult.function.file);
        // is this function prepared?
        if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
            log.info("100.560 Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
            functionPrepareResult.isLoaded = false;

            setFunctionDownloadStatus(assetManagerUrl, function.code, EnumsApi.FunctionSourcing.dispatcher, EnumsApi.FunctionState.none);
        }
        return functionPrepareResult;
    }


    private static FunctionApiData.SystemExecResult verifyChecksumAndSignature(boolean signatureRequired, @Nullable PublicKey publicKey, TaskParamsYaml.FunctionConfig function) {
        if (!signatureRequired) {
            return new FunctionApiData.SystemExecResult(function.code, true, 0, "");
        }
        if (function.checksumMap==null) {
            final String es = "100.360 signature is required but function.checksumMap is null";
            log.error(es);
            return new FunctionApiData.SystemExecResult(function.code, false, -980, es);
        }

        // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing right now
        final EnumsApi.HashAlgo hashAlgo = EnumsApi.HashAlgo.SHA256WithSignature;
        String data = function.checksumMap.entrySet().stream()
            .filter(o -> o.getKey() == hashAlgo)
            .findFirst()
            .map(Map.Entry::getValue).orElse(null);

        if (S.b(data)) {
            String es = S.f("100.380 Global signatureRequired==true but function %s has empty value for algo %s", function.code, hashAlgo);
            log.error(es);
            return new FunctionApiData.SystemExecResult(function.code, false, -981, es);
        }
        ChecksumAndSignatureData.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
        if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
            String es = S.f("100.400 Global isFunctionSignatureRequired==true but function %s has empty checksum or signature", function.code);
            log.error(es);
            return new FunctionApiData.SystemExecResult(function.code, false, -982, es);
        }

        String s = FunctionCoreUtils.getDataForChecksumForConfigOnly(function);
        String sum = Checksum.getChecksum(hashAlgo, new ByteArrayInputStream(s.getBytes()));
        if (!checksumWithSignature.checksum.equals(sum)) {
            String es = S.f("100.420 Function %s has a wrong checksum", function.code);
            log.error(es);
            return new FunctionApiData.SystemExecResult(function.code, false, -983, es);
        }
        // ###idea### why?
        //noinspection ConstantConditions
        EnumsApi.SignatureState st = ChecksumWithSignatureUtils.isValid(hashAlgo.signatureAlgo, sum.getBytes(), checksumWithSignature.signature, publicKey);
        if (st!= EnumsApi.SignatureState.correct) {
            if (!checksumWithSignature.checksum.equals(sum)) {
                String es = S.f("100.440 Function %s has wrong signature", function.code);
                log.error(es);
                return new FunctionApiData.SystemExecResult(function.code, false, -984, es);
            }
        }
        return new FunctionApiData.SystemExecResult(function.code, true, 0, "");
    }

    private static Map<String, EnumsApi.FunctionState> parseToMapOfStates( FunctionRepositoryData.FunctionDownloadStatuses functionDownloadStatus) {
        Map<String, EnumsApi.FunctionState> map = new HashMap<>();
        for (Map.Entry<EnumsApi.FunctionState, String> entry : functionDownloadStatus.statuses.entrySet()) {
            String[] names = entry.getValue().split(",");
            for (String name : names) {
                map.put(name, entry.getKey());
            }
        }
        return map;
    }

/*
    Map<String, EnumsApi.FunctionState> mapOfFunctionStates = parseToMapOfStates(functionDownloadStatus);
*/

}
