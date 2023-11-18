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
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
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
    private final DownloadFunctionService downloadFunctionService;


    // key - assetManagerUrl, value - Map with key - function code, value - FunctionRepositoryData.Function
    private static final ConcurrentHashMap<ProcessorAndCoreData.AssetManagerUrl, ConcurrentHashMap<String, FunctionRepositoryData.DownloadStatus>> functions = new ConcurrentHashMap<>();

    @Nullable
    public FunctionRepositoryRequestParams processFunctionRepositoryResponseParams(
        ProcessorEnvironment processorEnvironment,
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl, FunctionRepositoryResponseParams responseParams, @Nullable Long processorId) {

        List<String> codesReady = new ArrayList<>();
        // failed download of function won't be reported. so dispatcher just won't assign task to this processor
//        List<String> codesFailed = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(responseParams.functionCodes)) {
            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
            final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

            for (String functionCode : responseParams.functionCodes) {
                FunctionRepositoryData.DownloadStatus f = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(functionCode);
                if (f!=null) {
                    if (f.state==ready) {
                        codesReady.add(functionCode);
                    }
                    else if (f.state.failed) {
                        log.warn("816.030 function {} is active but failed to be downloaded. assetManagerUrl: {}", functionCode, assetManagerUrl.url);
//                        codesFailed.add(functionCode);
                    }
                }
                downloadFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionCode, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, HIGH));
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
    public static FunctionRepositoryData.DownloadStatus getFunctionDownloadStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        FunctionRepositoryData.DownloadStatus status = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(functionCode);
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
            FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, info.getValue());
/*
            FunctionRepositoryData.Function status = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).values().stream()
                .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(info.getValue()))
                .findFirst().orElse(null);
*/
            if (status==null || status.state == not_found) {
                removeFunction(assetManagerUrl, info.getValue());
//                setFunctionDownloadStatusInternal(assetManagerUrl, info.getValue(), info.getKey(), none);
                isChanged = true;
            }
        }

/*
        // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
        for (FunctionRepositoryData.Function status : functions.values()) {
            if (status.assetManagerUrl.equals(assetManagerUrl.url) && list.stream().filter(i-> i.getValue().equals(status.code)).findFirst().orElse(null)==null) {
                setFunctionDownloadStatusInternal(assetManagerUrl, status.code, status.sourcing, not_found);
                isChanged = true;
            }
        }
*/
        if (isChanged) {
//            updateMetadataFile();
        }
    }

    @Nullable
    public FunctionRepositoryData.DownloadStatus setFunctionState(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.240 functionCode is null");
        }
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
//        FunctionRepositoryData.Function status = functions.values().stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
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
            throw new IllegalStateException("816.260 functionCode is null");
        }
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
//        FunctionRepositoryData.Function status = functions.values().stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.state = ready;
        status.checksum= EnumsApi.ChecksumState.runtime;
        status.signature= EnumsApi.SignatureState.runtime;
        status.lastCheck = System.currentTimeMillis();

    }

    public void setChecksumMap(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, @Nullable Map<EnumsApi.HashAlgo, String> checksumMap) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.280 functionCode is null");
        }
        if (checksumMap==null) {
            return;
        }
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
//        FunctionRepositoryData.Function status = functions.values().stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.checksumMap.putAll(checksumMap);
        status.lastCheck = System.currentTimeMillis();
    }

    public static void setChecksumAndSignatureStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.300 functionCode is null");
        }
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
//        FunctionRepositoryData.Function status = functions.values().stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {
            return;
        }
        status.checksum = checkSumAndSignatureStatus.checksum;
        status.signature = checkSumAndSignatureStatus.signature;
        status.lastCheck = System.currentTimeMillis();
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

    public void setFunctionDownloadStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, FunctionState functionState, DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.360 functionCode is empty");
        }
        setFunctionDownloadStatusInternal(assetManagerUrl, functionCode, sourcing, functionState, dispatcher);
//        updateMetadataFile();
    }

    public Map<FunctionState, String> getAsFunctionDownloadStatuses(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
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
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
//        FunctionRepositoryData.DownloadStatus status = functions.values().stream().filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(functionCode)).findFirst().orElse(null);
        if (status == null) {

            status = new FunctionRepositoryData.DownloadStatus(
                none, functionCode, assetManagerUrl, sourcing,
                EnumsApi.ChecksumState.not_yet, EnumsApi.SignatureState.not_yet, System.currentTimeMillis());

            downloadFunctionService.addTask(new FunctionRepositoryData.DownloadFunctionTask(functionCode, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, NORMAL));
        }
        status.state = functionState;
        status.lastCheck = System.currentTimeMillis();
    }

/*
    public Collection<FunctionRepositoryData.DownloadStatus> getStatuses() {
        return Collections.unmodifiableCollection(functions.values());
    }
*/

/*
    private static void markAllAsUnverified() {
        List<FunctionRepositoryData.DownloadStatus> forRemoving = new ArrayList<>();
        for (FunctionRepositoryData.DownloadStatus status : functions.values()) {
            if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                continue;
            }
            if (status.state == not_found) {
                forRemoving.add(status);
            }
            status.state = none;
            status.checksum = EnumsApi.ChecksumState.not_yet;
            status.signature = EnumsApi.SignatureState.not_yet;
        }
        for (FunctionRepositoryData.DownloadStatus status : forRemoving) {
            removeFunction(status.assetManagerUrl, status.code);
        }
    }
*/

    @Nullable
    public FunctionRepositoryData.FunctionConfigAndStatus syncFunctionStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, final String functionCode) {
        try {
            return syncFunctionStatusInternal(assetManagerUrl, assetManager, functionCode);
        } catch (Throwable th) {
            log.error("816.080 Error in syncFunctionStatus()", th);
            return new FunctionRepositoryData.FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, io_error));
        }
    }

    @Nullable
    private FunctionRepositoryData.FunctionConfigAndStatus syncFunctionStatusInternal(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {
        FunctionRepositoryData.DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);

        if (status == null) {
            return null;
        }
        if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
            log.warn("816.090 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, assetManagerUrl.url);
            return null;
        }
        if (status.state == ready) {
            if (status.checksum!= EnumsApi.ChecksumState.not_yet && status.signature!= EnumsApi.SignatureState.not_yet) {
                return new FunctionRepositoryData.FunctionConfigAndStatus(status);
            }
            setFunctionState(assetManagerUrl, functionCode, none);
        }

        FunctionRepositoryData.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
            ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);

        if (downloadedFunctionConfigStatus.status==FunctionEnums.ConfigStatus.error) {
            return new FunctionRepositoryData.FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, function_config_error));
        }
        if (downloadedFunctionConfigStatus.status== FunctionEnums.ConfigStatus.not_found) {
            removeFunction(assetManagerUrl, functionCode);
            return null;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;
        setChecksumMap(assetManagerUrl, functionCode, functionConfig.checksumMap);

        if (S.b(functionConfig.file)) {
            log.error("816.100 name of file for function {} is blank and content of function is blank too", functionCode);
            return new FunctionRepositoryData.FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, function_config_error));
        }

        Path baseFunctionDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);

        final AssetFile assetFile = AssetUtils.prepareFunctionAssetFile(baseFunctionDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            return new FunctionRepositoryData.FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, asset_error));
        }
        if (!assetFile.isContent) {
            return new FunctionRepositoryData.FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, none), assetFile);
        }

        return new FunctionRepositoryData.FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, ok), assetFile);
    }

    public FunctionRepositoryData.FunctionPrepareResult prepareFunction(DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function) {
        try {
            final MetadataParams metadataParams = processorEnvironment.metadataParams;
            if (function.sourcing== EnumsApi.FunctionSourcing.dispatcher) {
                return prepareWithSourcingAsDispatcher(assetManagerUrl, function, globals, dispatcher);
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.git) {
                return prepareWithSourcingAsGit(globals.processorResourcesPath, assetManagerUrl, function, metadataParams, gitSourcingService::prepareFunction);
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
        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, Globals Globals,
        DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher) {
        FunctionRepositoryData.FunctionPrepareResult functionPrepareResult = new FunctionRepositoryData.FunctionPrepareResult();
        functionPrepareResult.function = function;

        final Path baseResourceDir = MetadataParams.prepareBaseDir(Globals.processorResourcesPath, assetManagerUrl);
        functionPrepareResult.functionAssetFile = AssetUtils.prepareFunctionAssetFile(baseResourceDir, functionPrepareResult.function.getCode(), functionPrepareResult.function.file);
        // is this function prepared?
        if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
            log.info("100.560 Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
            functionPrepareResult.isLoaded = false;

            setFunctionDownloadStatus(assetManagerUrl, function.code, EnumsApi.FunctionSourcing.dispatcher, none, dispatcher);
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

    private static Map<String, FunctionState> parseToMapOfStates( FunctionRepositoryData.FunctionDownloadStatuses functionDownloadStatus) {
        Map<String, FunctionState> map = new HashMap<>();
        for (Map.Entry<FunctionState, String> entry : functionDownloadStatus.statuses.entrySet()) {
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
