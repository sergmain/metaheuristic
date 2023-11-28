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
import ai.metaheuristic.commons.system.SystemProcessLauncher;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.EnumsApi.FunctionState;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.HIGH;
import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.NORMAL;
import static ai.metaheuristic.ai.functions.FunctionRepositoryData.*;
import static ai.metaheuristic.api.EnumsApi.FunctionState.*;

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
    private final ApplicationEventPublisher eventPublisher;


    // key - assetManagerUrl, value - Map with key - function code, value - FunctionRepositoryData.Function
    private static final ConcurrentHashMap<ProcessorAndCoreData.AssetManagerUrl, ConcurrentHashMap<String, DownloadStatus>> functions = new ConcurrentHashMap<>();

    @Nullable
    public FunctionRepositoryRequestParams processFunctionRepositoryResponseParams(
        ProcessorEnvironment processorEnvironment,
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl, FunctionRepositoryResponseParams responseParams, @Nullable Long processorId) {

        List<String> codesReady = new ArrayList<>();
        // failed download of function won't be reported. so dispatcher just won't assign task to this processor
//        List<String> codesFailed = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(responseParams.functions)) {
            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
            final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

            for (FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig : responseParams.functions) {
                DownloadStatus f = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(shortFunctionConfig.code);
                if (f!=null) {
                    if (f.state==ready) {
                        codesReady.add(shortFunctionConfig.code);
                    }
                    else if (f.state.failed) {
                        log.warn("816.030 function {} is active but failed to be downloaded. assetManagerUrl: {}", shortFunctionConfig.code, assetManagerUrl.url);
//                        codesFailed.add(functionCode);
                    }
                }
                eventPublisher.publishEvent(new DownloadFunctionTask(shortFunctionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, HIGH));
//                downloadFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionCode, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, HIGH));
            }
        }
        if (processorId!=null && !codesReady.isEmpty()) {
            FunctionRepositoryRequestParams r = new FunctionRepositoryRequestParams();
            r.functionCodes = codesReady;
            r.processorId = processorId;
            return r;
        }
        return null;
    }

    @Nullable
    public static DownloadStatus getFunctionDownloadStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        DownloadStatus status = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(functionCode);
        return status;
    }

    public void registerNewFunctionCode(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, Map<EnumsApi.FunctionSourcing, String> infos) {
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
            DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, info.getValue());
            if (status==null || status.state == not_found) {
                removeFunction(assetManagerUrl, info.getValue());
                isChanged = true;
            }
        }
        if (isChanged) {
//            updateMetadataFile();
        }
    }

    @Nullable
    public DownloadStatus setFunctionState(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.240 functionCode is null");
        }
        final DownloadStatus status = new DownloadStatus(functionState, functionCode, assetManagerUrl, EnumsApi.FunctionSourcing.dispatcher);
        functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).put(functionCode, status);
        return status;
    }

    public static void setFunctionFromProcessorAsReady(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.260 functionCode is null");
        }
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (status == null) {
            return;
        }
        status.state = ready;
    }

    public static void setChecksumMap(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, @Nullable Map<EnumsApi.HashAlgo, String> checksumMap) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.280 functionCode is null");
        }
        if (checksumMap==null) {
            return;
        }
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (status == null) {
            return;
        }
    }

    public static void setChecksumAndSignatureStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.300 functionCode is null");
        }
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (status == null) {
            return;
        }
    }

    private static void removeFunction(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.320 functionCode is empty");
        }
        var map = functions.get(assetManagerUrl);
        if (map==null) {
            return;
        }
        map.remove(functionCode);
    }

/*
    public void setFunctionDownloadStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, FunctionState functionState, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.360 functionCode is empty");
        }
        setFunctionDownloadStatusInternal(assetManagerUrl, functionCode, sourcing, functionState, dispatcher);
    }
*/

    public static Map<FunctionState, String> getAsFunctionDownloadStatuses(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        final Map<FunctionState, List<String>> map = new HashMap<>();
        functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).values()
            .forEach(o->map.computeIfAbsent(o.state, (k)->new ArrayList<>()).add(o.code));

        final Map<FunctionState, String> infos = new HashMap<>();
        for (Map.Entry<FunctionState, List<String>> entry : map.entrySet()) {
            infos.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        return infos;
    }

    private void setFunctionDownloadStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, FunctionState functionState, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher) {
/*
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (status == null) {

            status = new DownloadStatus(none, functionCode, assetManagerUrl, sourcing);

            eventPublisher.publishEvent(new DownloadFunctionTask(functionCode, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
        }
        status.state = functionState;
*/
    }

    @Nullable
    public FunctionConfigAndStatus syncFunctionStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, final String functionCode) {
        try {
            return syncFunctionStatusInternal(assetManagerUrl, assetManager, functionCode);
        } catch (Throwable th) {
            log.error("816.080 Error in syncFunctionStatus()", th);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, io_error));
        }
    }

    @Nullable
    private FunctionConfigAndStatus syncFunctionStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);

        if (status == null) {
            return null;
        }
        if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
            log.warn("816.090 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, assetManagerUrl.url);
            return null;
        }
        if (status.state == ready) {
            return new FunctionConfigAndStatus(status);
        }

        DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
            ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);

        if (downloadedFunctionConfigStatus.status==FunctionEnums.ConfigStatus.error) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, function_config_error));
        }
        if (downloadedFunctionConfigStatus.status== FunctionEnums.ConfigStatus.not_found) {
            removeFunction(assetManagerUrl, functionCode);
            return null;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;
        setChecksumMap(assetManagerUrl, functionCode, functionConfig.checksumMap);

        if (S.b(functionConfig.file)) {
            log.error("816.100 name of file for function {} is blank and content of function is blank too", functionCode);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, function_config_error));
        }

        Path baseFunctionDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);

        final AssetFile assetFile = AssetUtils.prepareFunctionAssetFile(baseFunctionDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, asset_error));
        }
        if (!assetFile.isContent) {
            return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, none), assetFile);
        }

        return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, ok), assetFile);
    }

    public FunctionPrepareResult prepareFunction(DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function) {
        try {
            final MetadataParams metadataParams = processorEnvironment.metadataParams;
            if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher) {
                return prepareWithSourcingAsDispatcher(assetManagerUrl, function, globals, dispatcher);
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.git) {
                return prepareWithSourcingAsGit(globals.processorResourcesPath, assetManagerUrl, function, metadataParams, gitSourcingService::prepareFunction);
            }
            throw new IllegalStateException("100.460 Shouldn't get there");
        } catch (Throwable th) {
            String es = "100.480 System error: " + th.getMessage();
            log.error(es, th);
            FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
            functionPrepareResult.function = function;
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1000, es);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
    }

    private static FunctionPrepareResult prepareWithSourcingAsGit(Path processorResourcesPath, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, MetadataParams metadataParams, BiFunction<Path, TaskParamsYaml.FunctionConfig, SystemProcessLauncher.ExecResult> gitSourcing) {
        FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
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

    private FunctionPrepareResult prepareWithSourcingAsDispatcher(
        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, Globals Globals,
        DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher) {

        FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
        functionPrepareResult.function = function;

        final Path baseResourceDir = MetadataParams.prepareBaseDir(Globals.processorResourcesPath, assetManagerUrl);
        final String actualFunctionFile = AssetUtils.getActualFunctionFile(functionPrepareResult.function);
        if (actualFunctionFile==null) {
            log.error("100.320 actualFunctionFile is null");
            setFunctionState(assetManagerUrl, functionPrepareResult.function.getCode(), EnumsApi.FunctionState.asset_error);
            return new FunctionPrepareResult(function, null, new FunctionApiData.SystemExecResult(function.code, false, -995, "" ), false, true);
        }
        functionPrepareResult.functionAssetFile = AssetUtils.prepareFunctionAssetFile(baseResourceDir, functionPrepareResult.function.getCode(), actualFunctionFile);

        // is this function prepared?
        if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
            log.info("100.340 Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
            functionPrepareResult.isLoaded = false;

            return functionPrepareResult;
            // setFunctionDownloadStatus(assetManagerUrl, function.code, EnumsApi.FunctionSourcing.dispatcher, none, dispatcher);
        }
        DownloadStatus functionDownloadStatus = FunctionRepositoryProcessorService.getFunctionDownloadStatus(assetManagerUrl, function.code);
        if (functionDownloadStatus!=null) {
            return new FunctionPrepareResult(function, functionPrepareResult.functionAssetFile, new FunctionApiData.SystemExecResult(function.code, true, 0, "" ), true, false);
        }
        functionPrepareResult.isLoaded = false;
        functionPrepareResult.isError = true;

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

    private static Map<String, FunctionState> parseToMapOfStates( FunctionDownloadStatuses functionDownloadStatus) {
        Map<String, FunctionState> map = new HashMap<>();
        for (Map.Entry<FunctionState, String> entry : functionDownloadStatus.statuses.entrySet()) {
            String[] names = entry.getValue().split(",");
            for (String name : names) {
                map.put(name, entry.getKey());
            }
        }
        return map;
    }
}
