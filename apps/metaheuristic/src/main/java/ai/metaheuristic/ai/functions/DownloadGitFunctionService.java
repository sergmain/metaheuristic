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
import ai.metaheuristic.commons.utils.BundleUtils;
import ai.metaheuristic.commons.utils.DirUtils;
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
    private final FunctionRepositoryProcessorService functionRepositoryProcessorService;

    private final MultiTenantedQueue<FunctionEnums.DownloadPriority, FunctionRepositoryData.DownloadFunctionTask> downloadFunctionQueue =
        new MultiTenantedQueue<>(100, Duration.ZERO, true, "git-clone-", this::downloadFunction);

    public void addTask(FunctionRepositoryData.DownloadFunctionTask task) {
        downloadFunctionQueue.putToQueue(task);
    }

    @Async
    @EventListener
    public void processAssetPreparing(FunctionRepositoryData.DownloadFunctionTask event) {
        try {
            addTask(event);
        } catch (Throwable th) {
            log.error("817.020 Error, need to investigate ", th);
        }
    }

    public void downloadFunction(FunctionRepositoryData.DownloadFunctionTask task) {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        if (task.shortFunctionConfig.sourcing!= EnumsApi.FunctionSourcing.git) {
            log.warn("817.040 Attempt to download function from git but sourcing is" + task.shortFunctionConfig.sourcing);
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

        final DispatcherData.DispatcherContextInfo contextInfo = getDispatcherContextInfo(assetManagerUrl);
        if (contextInfo == null) {
            return;
        }

        FunctionRepositoryData.DownloadedFunctionConfigStatus status = ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);
        Path baseFunctionDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);

        final String actualFunctionFile = AssetUtils.getActualFunctionFile(status.functionConfig);
        if (actualFunctionFile==null) {
            log.error("811.010 actualFunctionFile is null");
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error);
            return;
        }
        final AssetFile assetFile = AssetUtils.prepareFunctionAssetFile(baseFunctionDir, functionCode, actualFunctionFile);
        if (assetFile.isError) {
            log.error("811. 015 AssetFile error creation for function " + functionCode + " encountered");
            return;
        }

        Path parentDir = DirUtils.getParent(assetFile.file, Path.of(actualFunctionFile));
        if (parentDir==null) {
            log.error("811.070 parentDir is null");
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error);
            return;
        }

        try {
            Files.createDirectories(parentDir);

            BundleData.Cfg cfg = new BundleData.Cfg(null, parentDir, task.shortFunctionConfig.git);
            BundleUtils.initRepo(cfg);

            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ready);


            int i=0;
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
