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
package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.MetadataService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionService;
import ai.metaheuristic.ai.processor.tasks.DownloadFunctionTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DownloadFunctionActor extends AbstractTaskQueue<DownloadFunctionTask> {

    private final Globals globals;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorFunctionService processorFunctionService;

    @SuppressWarnings("Duplicates")
    public void downloadFunctions() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        DownloadFunctionTask task;
        while((task = poll())!=null) {
            final String functionCode = task.functionCode;
            final DispatcherLookupConfig.DispatcherLookup dispatcher = task.dispatcher;
            final DispatcherLookupConfig.Asset asset = dispatcher.asset!=null
                    ? dispatcher.asset
                    : new DispatcherLookupConfig.Asset(dispatcher.url, dispatcher.restUsername, dispatcher.restPassword);

            if (task.chunkSize==null) {
                log.error("#811.007 (task.chunkSize==null), dispatcher.url: {}, asset.url: {}", dispatcher.url, asset.url);
                continue;
            }

            // it could be null if this function was deleted
            FunctionDownloadStatusYaml.Status sdsy = metadataService.syncFunctionStatus(dispatcher.url, asset, functionCode);
            if (sdsy==null || sdsy.functionState == Enums.FunctionState.ready) {
                continue;
            }

            final FunctionDownloadStatusYaml.Status functionDownloadStatus = metadataService.getFunctionDownloadStatuses(dispatcher.url, functionCode);
            if (functionDownloadStatus.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                log.warn("#811.010 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, dispatcher.url);
                continue;
            }
            if (functionDownloadStatus.functionState != Enums.FunctionState.none) {
                log.warn("#811.013 Function {} from {} was already processed and has a state {}.", functionCode, dispatcher.url, functionDownloadStatus.functionState);
                continue;
            }

            // task.functionConfig is null when we are downloading a function proactively, without any task
            TaskParamsYaml.FunctionConfig functionConfig = task.functionConfig;
            if (functionConfig ==null) {
                ProcessorFunctionService.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus = processorFunctionService.downloadFunctionConfig(dispatcher.url, asset, functionCode, task.processorId);
                if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.error) {
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.function_config_error);
                    continue;
                }
                if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.not_found) {
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.not_found);
                    continue;
                }
                functionConfig = downloadedFunctionConfigStatus.functionConfig;
            }
            MetadataService.ChecksumState checksumState = metadataService.prepareChecksum(functionCode, dispatcher.url, functionConfig);
            if (!checksumState.signatureIsOk) {
                continue;
            }
            Checksum checksum = checksumState.getChecksum();

            final Metadata.DispatcherInfo dispatcherInfo = metadataService.dispatcherUrlAsCode(dispatcher.url);
            final File baseResourceDir = dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherInfo);
            final AssetFile assetFile = ResourceUtils.prepareFunctionFile(baseResourceDir, functionCode, functionConfig.file);

            switch (functionDownloadStatus.functionState) {
                case none:
                    if (assetFile.isError) {
                        metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.asset_error);
                        continue;
                    }
                    break;
                case ok:
                    log.error("#811.020 Unexpected state of function {}, dispatcher {}, will be resetted to none", functionCode, dispatcher.url);
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.none);
                    break;
                case signature_wrong:
                case signature_not_found:
                case checksum_wrong:
                case not_supported_os:
                case asset_error:
                case download_error:
                    log.warn("#811.030 Function {} can't be downloaded from {}. The current status is {}", functionCode, dispatcher.url, functionDownloadStatus.functionState);
                    continue;
                case function_config_error:
                    log.warn("#811.030 Config for function {} wasn't downloaded from {}, State will be reseted to none", functionCode, dispatcher.url);
                    // reset to none for trying again
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.none);
                    continue;
                case not_found:
                    log.warn("#811.033 Config for function {} wasn't found on {}, State will be reseted to none", functionCode, dispatcher.url);
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.none);
                    continue;
                case ready:
                    if (assetFile.isError || !assetFile.isContent ) {
                        log.warn("#811.040 Function {} from {} is broken. Set state to asset_error", functionCode, dispatcher.url);
                        metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.asset_error);
                    }
                    continue;
            }

            try {

                File functionTempFile = new File(assetFile.file.getAbsolutePath() + ".tmp");

                final String targetUrl = asset.url + Consts.REST_ASSET_URL + "/function";

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.FunctionState resourceState = Enums.FunctionState.none;
                int idx = 0;
                do {
                    final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8) +'-' + task.processorId;
                    try {
                        final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("code", task.functionCode)
                                .addParameter("chunkSize", task.chunkSize!=null ? task.chunkSize.toString() : "")
                                .addParameter("chunkNum", Integer.toString(idx));

                        final Request request = Request.Get(builder.build())
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
                        File partFile = new File(dir, String.format(mask, idx));

                        final HttpResponse httpResponse = response.returnResponse();
                        try (final FileOutputStream out = new FileOutputStream(partFile)) {
                            final HttpEntity entity = httpResponse.getEntity();
                            if (entity != null) {
                                entity.writeTo(out);
                            }
                            else {
                                log.warn("#811.050 http entity is null");
                            }
                        }
                        final Header[] headers = httpResponse.getAllHeaders();
                        if (!DownloadUtils.isChunkConsistent(partFile, headers)) {
                            log.error("#811.060 error while downloading chunk of function {}, size is different", functionCode);
                            metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                        if (DownloadUtils.isLastChunk(headers)) {
                            resourceState = Enums.FunctionState.ok;
                            break;
                        }
                        if (partFile.length()==0) {
                            resourceState = Enums.FunctionState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            final String es = S.f("#811.070 Function %s wasn't found on dispatcher %s.", task.functionCode, dispatcher.url);
                            log.warn(es);
                            metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.not_found);
                            resourceState = Enums.FunctionState.not_found;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            final String es = S.f("#811.080 Unknown error with a function %s from dispatcher %s.", task.functionCode, dispatcher.url);
                            log.warn(es);
                            metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            final String es = S.f("#811.090 Unknown error with a resource %s from dispatcher %s.", task.functionCode, dispatcher.url);
                            log.warn(es);
                            metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                    }
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.FunctionState.none) {
                    log.error("#811.100 something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.FunctionState.download_error || resourceState == Enums.FunctionState.not_found) {
                    log.warn("#811.110 function {} can't be acquired, state: {}", functionCode, resourceState);
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(functionTempFile)) {
                    for (int i = 0; i <= idx; i++) {
                        final File input = new File(assetFile.file.getAbsolutePath() + "." + i + ".tmp");
                        if (input.length()==0) {
                            continue;
                        }
                        FileUtils.copyFile(input, fos);
                    }
                }

                CheckSumAndSignatureStatus status = metadataService.getCheckSumAndSignatureStatus(functionCode, dispatcher, checksum, functionTempFile);
                if (status.checksum==CheckSumAndSignatureStatus.Status.correct && status.signature==CheckSumAndSignatureStatus.Status.correct) {
                    //noinspection ResultOfMethodCallIgnored
                    functionTempFile.renameTo(assetFile.file);
                    metadataService.setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.ready);
                }
                else {
                    //noinspection ResultOfMethodCallIgnored
                    functionTempFile.delete();
                }
            }
            catch (HttpResponseException e) {
                logError(functionCode, e);
            }
            catch (SocketTimeoutException e) {
                log.error("#811.140 SocketTimeoutException: {}", e.toString());
            }
            catch (IOException e) {
                log.error("#811.150 IOException", e);
            } catch (URISyntaxException e) {
                log.error("#811.160 URISyntaxException", e);
            }
        }
    }

    public void prepareFunctionForDownloading() {
        metadataService.getFunctionDownloadStatusYaml().statuses.forEach(o -> {
            if (o.sourcing== EnumsApi.FunctionSourcing.dispatcher && (o.functionState == Enums.FunctionState.none || !o.verified)) {
                final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                        dispatcherLookupExtendedService.lookupExtendedMap.get(o.dispatcherUrl);

                if (dispatcher==null || dispatcher.context.chunkSize==null) {
                    log.info("#811.195 (dispatcher==null || dispatcher.config.chunkSize==null), dispatcherUrl: {}", o.dispatcherUrl);
                    return;
                }

                log.info("Create new DownloadFunctionTask for downloading function {} from {}, chunck size: {}",
                        o.code, o.dispatcherUrl, dispatcher.context.chunkSize);
                Metadata.DispatcherInfo dispatcherInfo = metadataService.dispatcherUrlAsCode(o.dispatcherUrl);

                DownloadFunctionTask functionTask = new DownloadFunctionTask(dispatcher.context.chunkSize, o.code, null);
                functionTask.dispatcher = dispatcher.dispatcherLookup;
                functionTask.processorId = dispatcherInfo.processorId;
                add(functionTask);
            }
        });
    }

    private void logError(String functionCode, HttpResponseException e) {
        if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
            log.warn("#811.200 Function with code {} wasn't found", functionCode);
        }
        else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
            log.warn("#811.210 Function with code {} is broken and need to be recreated", functionCode);
        }
        else {
            log.error("#811.220 HttpResponseException", e);
        }
    }

}