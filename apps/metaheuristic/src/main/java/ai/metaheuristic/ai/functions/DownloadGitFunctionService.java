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
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.processor.DispatcherContextInfoHolder;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.actors.GetDispatcherContextInfoService;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.tasks.GetDispatcherContextInfoTask;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.BundleUtils;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static ai.metaheuristic.commons.CommonConsts.GIT_REPO;

/**
 * @author Sergio Lissner
 * Date: 11/27/2023
 * Time: 9:54 PM
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DownloadGitFunctionService {

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;
    private final GetDispatcherContextInfoService getDispatcherContextInfoService;

    private final MultiTenantedQueue<FunctionEnums.DownloadPriority, FunctionRepositoryData.DownloadGitFunctionTask> downloadFunctionQueue =
        new MultiTenantedQueue<>(100, Duration.ZERO, true, "git-clone-", this::getGitRepo);

    public void addTask(FunctionRepositoryData.DownloadGitFunctionTask task) {
        downloadFunctionQueue.putToQueue(task);
    }

    @Async
    @EventListener
    public void processAssetPreparing(FunctionRepositoryData.DownloadGitFunctionTask event) {
        try {
            addTask(event);
        } catch (Throwable th) {
            log.error("817.020 Error, need to investigate ", th);
        }
    }

    public static class GitRepoSync {
        private static final CommonSync<String> commonSync = new CommonSync<>();

        public static void getWithSyncVoid(final String repo, Runnable runnable) {
            final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(repo);
            try {
                lock.lock();
                runnable.run();
            } finally {
                lock.unlock();
            }
        }
    }

    public void getGitRepo(FunctionRepositoryData.DownloadGitFunctionTask task) {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        if (task.shortFunctionConfig.sourcing!= EnumsApi.FunctionSourcing.git) {
            log.warn("817.040 Attempt to download function from git but sourcing is {}", task.shortFunctionConfig.sourcing);
            return;
        }

        // created for easier debugging
        final String functionCode = task.functionCode;
        final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = task.assetManagerUrl;

        if (FunctionRepositoryProcessorService.getFunctionDownloadStatus(task.assetManagerUrl, task.functionCode)!=null) {
            // already downloaded
            return;
        }

        final DispatcherLookupParamsYaml.AssetManager assetManager = processorEnvironment.dispatcherLookupExtendedService.getAssetManager(assetManagerUrl);
        if (assetManager==null) {
            log.error("817.080 assetManager server wasn't found for url {}", assetManagerUrl.url);
            return;
        }
        if (task.shortFunctionConfig.git==null) {
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error, null);
            return;
        }

        final DispatcherData.DispatcherContextInfo contextInfo = getDispatcherContextInfo(assetManagerUrl);
        if (contextInfo == null) {
            return;
        }

        FunctionRepositoryData.DownloadedFunctionConfigStatus status = ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);

        final String actualFunctionFile = AssetUtils.getActualFunctionFile(status.functionConfig);
        if (actualFunctionFile==null) {
            log.error("811.010 actualFunctionFile is null");
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error, null);
            return;
        }

        GitRepoSync.getWithSyncVoid(task.shortFunctionConfig.git.repo, ()-> initGitRepo(task.shortFunctionConfig.git, assetManagerUrl, functionCode, actualFunctionFile));
    }

    private void initGitRepo(GitInfo gitInfo, ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode, String actualFunctionFile) {
        try {
            Path baseResourcePath = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);
            Path baseRepoPath = baseResourcePath.resolve(GIT_REPO);
            Files.createDirectories(baseRepoPath);

            BundleData.Cfg cfg = new BundleData.Cfg(null, baseRepoPath, gitInfo);
            BundleUtils.initRepo(cfg);

            final AssetFile assetFile = new AssetFile();
            assetFile.file = cfg.repoDir.resolve(gitInfo.path).resolve(actualFunctionFile);

            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ready, assetFile);

            int i=0;
            return;
        } catch (HttpResponseException e) {
            logError(functionCode, e);
        } catch (SocketTimeoutException e) {
            log.error("817.500 SocketTimeoutException: {}", e.toString());
        } catch (ConnectException e) {
            log.error("817.520 ConnectException: {}", e.toString());
        } catch (IOException e) {
            log.error("817.540 IOException", e);
        } catch (Throwable th) {
            log.error("817.580 Throwable", th);
        }
        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.io_error, null);
    }

    @Nullable
    private DispatcherData.DispatcherContextInfo getDispatcherContextInfo(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        final DispatcherData.DispatcherContextInfo contextInfo = DispatcherContextInfoHolder.getCtx(assetManagerUrl);

        // process only if dispatcher has already sent its config
        if (contextInfo==null || contextInfo.chunkSize==null) {
            log.warn("817.640 Asset dispatcher {} wasn't initialized yet, chunkSize is  undefined", assetManagerUrl.url);
            getDispatcherContextInfoService.add(new GetDispatcherContextInfoTask(assetManagerUrl));
            return null;
        }
        return contextInfo;
    }

    private static void logError(String functionCode, HttpResponseException e) {
        if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
            log.warn("817.660 Function with code {} wasn't found", functionCode);
        }
        else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
            log.warn("817.680 Function with code {} is broken and need to be recreated", functionCode);
        }
        else {
            log.error("817.700 HttpResponseException", e);
        }
    }

}
