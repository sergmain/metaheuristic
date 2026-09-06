/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.commons.utils.GitCommitCache;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.EnumsApi.FunctionState;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.HIGH;
import static ai.metaheuristic.ai.functions.FunctionRepositoryData.*;
import static ai.metaheuristic.api.EnumsApi.FunctionState.ready;

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
    private final ApplicationEventPublisher eventPublisher;

    // key - assetManagerUrl, value - Map with key - function code, value - FunctionRepositoryData.Function
    private static final ConcurrentHashMap<ProcessorAndCoreData.AssetManagerUrl, ConcurrentHashMap<String, DownloadStatus>> functions = new ConcurrentHashMap<>();

    @Nullable
    public FunctionRepositoryRequestParams processFunctionRepositoryResponseParams(
        ProcessorEnvironment processorEnvironment,
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl, FunctionRepositoryResponseParams responseParams, @Nullable Long processorId) {

        List<String> codesReady = new ArrayList<>();
        // failed download of function won't be reported. so dispatcher just won't assign task to this processor
        if (CollectionUtils.isNotEmpty(responseParams.functions)) {
            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = processorEnvironment.getProcessorEnv().dispatcherLookupExtendedService().lookupExtendedMap.get(dispatcherUrl);
            final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

            for (FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig : responseParams.functions) {
                if (shortFunctionConfig.sourcing==EnumsApi.FunctionSourcing.git) {
                    // ❗ A git-sourced Function has no asset-manager state to consult and must not be sent
                    // down the download path: its bytes come from a git revision, and readiness is simply
                    // whether that revision is materialized here. The Dispatcher only advertises one once an
                    // ExecContext has pinned it, so the commit on the wire is always a sha.
                    processAdvertisedGitFunction(shortFunctionConfig, codesReady);
                    continue;
                }
                DownloadStatus f = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(shortFunctionConfig.code);
                if (f!=null) {
                    if (f.state==ready) {
                        codesReady.add(shortFunctionConfig.code);
                    }
                    else if (f.state.failed) {
                        log.warn("816.030 function {} is active but failed to be downloaded, state: {}. assetManagerUrl: {}", shortFunctionConfig.code, f.state, assetManagerUrl.url);
                    }
                }
                eventPublisher.publishEvent(new DownloadFunctionTask(shortFunctionConfig.code, shortFunctionConfig, assetManagerUrl, dispatcher.dispatcherLookup.signatureRequired, HIGH));
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

    /**
     * Reports a git-sourced Function as ready once its pinned revision is materialized here, and asks for
     * it otherwise.
     *
     * <p>The reported key carries the sha, because the Dispatcher admits a Task against the revision that
     * Task is pinned to - reporting the bare code would claim readiness for revisions never fetched.
     */
    private void processAdvertisedGitFunction(
            FunctionRepositoryResponseParams.ShortFunctionConfig shortFunctionConfig, List<String> codesReady) {

        final GitInfo git = shortFunctionConfig.git;
        if (git==null || !GtiUtils.isSha(git.commit)) {
            log.warn("816.035 function {} was advertised as git-sourced with revision '{}', which is not a sha; "
                + "only a revision an ExecContext has resolved can be prepared",
                shortFunctionConfig.code, git==null ? null : git.commit);
            return;
        }
        final Path commits = GitCommitCache.commitsDir(
            globals.processorResourcesPath.resolve("git").resolve(StrUtils.asCode(git.repo)));

        if (GitCommitCache.isCached(commits, git.commit)) {
            codesReady.add(ExecutionGateService.readinessKey(shortFunctionConfig.code, git.commit));
            return;
        }
        eventPublisher.publishEvent(new DownloadGitFunctionTask(
            shortFunctionConfig.code, git, GIT_ADVERTISED_NO_TARGET_FILE, HIGH));
    }

    /**
     * The broadcast carries no targets - it exists to get the revision onto the Processor, and which file
     * inside it runs is decided per Task from the Task's own config.
     */
    private static final String GIT_ADVERTISED_NO_TARGET_FILE = "";

    @Nullable
    public static DownloadStatus getFunctionDownloadStatus(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        DownloadStatus status = functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).get(functionCode);
        return status;
    }

    @Nullable
    public static DownloadStatus setFunctionState(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, FunctionState functionState, @Nullable AssetFile assetFile) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.240 functionCode is null");
        }
        // every remaining caller is dispatcher-sourced: a git Function's readiness is the filesystem
        final DownloadStatus status = new DownloadStatus(functionState, functionCode, assetManagerUrl, EnumsApi.FunctionSourcing.dispatcher, assetFile);
        functions.computeIfAbsent(assetManagerUrl, (o)->new ConcurrentHashMap<>()).put(functionCode, status);
        return status;
    }

    public static void setChecksumAndSignatureStatus(final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("816.300 functionCode is null");
        }
        DownloadStatus status = getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (status == null) {
            return;
        }
        // TODO p3 2023.12.22 is this intentional or something is missed here?
    }

    public FunctionPrepareResult prepareFunction(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function) {
        try {
            if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher) {
                return prepareWithSourcingAsDispatcher(assetManagerUrl, function, globals.processorResourcesPath);
            }
            else if (function.sourcing== EnumsApi.FunctionSourcing.git) {
                return prepareWithSourcingAsGit(function, globals.processorResourcesPath);
            }
            throw new IllegalStateException("816.330 Shouldn't get there");
        } catch (Throwable th) {
            String es = "816.360 System error: " + th.getMessage();
            log.error(es, th);
            FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult();
            functionPrepareResult.function = function;
            functionPrepareResult.systemExecResult = new FunctionApiData.SystemExecResult(function.code, false, -1000, es);
            functionPrepareResult.isLoaded = false;
            functionPrepareResult.isError = true;
            return functionPrepareResult;
        }
    }

    /**
     * For a git-sourced Function the answer is on disk, not in a map: a materialized commit directory
     * exists or it doesn't, and {@link GitCommitCache} only publishes one by an atomic rename, so a
     * directory that exists is complete. There is no download status to consult and none to go stale -
     * which is what let a second ExecContext silently reuse the first one's revision.
     */
    private static FunctionPrepareResult prepareWithSourcingAsGit(
        TaskParamsYaml.FunctionConfig functionConfig, Path processorResourcesPath) {

        if (functionConfig.git==null) {
            throw new IllegalStateException("816.390 (functionConfig.git==null)");
        }
        final FunctionPrepareResult result = new FunctionPrepareResult(functionConfig);

        final String sha = functionConfig.git.commit;
        if (!GtiUtils.isSha(sha)) {
            final String es = S.f("816.395 Function %s has an unresolved git revision '%s'", functionConfig.code, sha);
            log.warn(es);
            result.systemExecResult = new FunctionApiData.SystemExecResult(functionConfig.code, false, -1, es);
            result.isLoaded = false;
            result.isError = true;
            return result;
        }

        final Path commits = GitCommitCache.commitsDir(
            processorResourcesPath.resolve("git").resolve(StrUtils.asCode(functionConfig.git.repo)));
        if (!GitCommitCache.isCached(commits, sha)) {
            result.isLoaded = false;
            return result;
        }

        final String actualFunctionFile = AssetUtils.getActualFunctionFile(functionConfig);
        if (actualFunctionFile==null) {
            final String es = S.f("816.400 actualFunctionFile is null for function %s", functionConfig.code);
            log.error(es);
            result.systemExecResult = new FunctionApiData.SystemExecResult(functionConfig.code, false, -1, es);
            result.isLoaded = false;
            result.isError = true;
            return result;
        }
        final AssetFile assetFile = new AssetFile();
        assetFile.file = GitCommitCache.entryPath(commits, sha)
            .resolve(functionConfig.git.path==null ? "" : functionConfig.git.path)
            .resolve(actualFunctionFile);
        assetFile.isContent = Files.exists(assetFile.file);
        result.functionAssetFile = assetFile;
        result.isLoaded = assetFile.isContent;
        return result;
    }

    private static FunctionPrepareResult prepareWithSourcingAsDispatcher(
        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, TaskParamsYaml.FunctionConfig function, Path processorResourcesPath) {

        FunctionPrepareResult functionPrepareResult = new FunctionPrepareResult(function);

        final Path baseResourceDir = MetadataParams.prepareBaseDir(processorResourcesPath, assetManagerUrl);
        final String actualFunctionFile = AssetUtils.getActualFunctionFile(functionPrepareResult.function);
        if (actualFunctionFile==null) {
            log.error("816.480 actualFunctionFile is null");
            setFunctionState(assetManagerUrl, functionPrepareResult.function.getCode(), EnumsApi.FunctionState.asset_error, null);
            return new FunctionPrepareResult(function, null, new FunctionApiData.SystemExecResult(function.code, false, -995, "" ), false, true);
        }
        functionPrepareResult.functionAssetFile = AssetUtils.prepareFunctionAssetFile(baseResourceDir, functionPrepareResult.function.getCode(), actualFunctionFile);

        // is this function prepared?
        if (functionPrepareResult.functionAssetFile.isError || !functionPrepareResult.functionAssetFile.isContent) {
            log.info("816.510 Function {} hasn't been prepared yet, {}", functionPrepareResult.function.code, functionPrepareResult.functionAssetFile);
            functionPrepareResult.isLoaded = false;

            return functionPrepareResult;
        }
        DownloadStatus functionDownloadStatus = FunctionRepositoryProcessorService.getFunctionDownloadStatus(assetManagerUrl, function.code);
        if (functionDownloadStatus!=null) {
            return new FunctionPrepareResult(function, functionPrepareResult.functionAssetFile, new FunctionApiData.SystemExecResult(function.code, true, 0, "" ), true, false);
        }
        functionPrepareResult.isLoaded = false;
        functionPrepareResult.isError = true;

        return functionPrepareResult;
    }
}
