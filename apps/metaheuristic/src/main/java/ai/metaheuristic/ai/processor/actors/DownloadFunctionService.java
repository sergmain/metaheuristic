/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.*;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.DownloadFunctionTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DownloadFunctionService extends AbstractTaskQueue<DownloadFunctionTask> {

    private final Globals globals;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorFunctionService processorFunctionService;
    private final DispatcherContextService dispatcherContextService;

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
            final DispatcherLookupParamsYaml.DispatcherLookup dispatcher1 = task.dispatcher;
            final ProcessorAndCoreData.DispatcherServerUrl dispatcherUrl = task.dispatcherUrl;
            final DispatcherLookupParamsYaml.Asset asset = task.getAsset();

            final ProcessorAndCoreData.ServerUrls serverUrls = new ProcessorAndCoreData.ServerUrls(
                    new ProcessorAndCoreData.DispatcherServerUrl(dispatcherUrl.url), new ProcessorAndCoreData.AssetServerUrl(asset.url));

            if (task.chunkSize==null) {
                log.error("#811.007 (task.chunkSize==null), dispatcher.url: {}, asset.url: {}", dispatcherUrl, asset.url);
                continue;
            }

            MetadataParamsYaml.Status sdsy = metadataService.syncFunctionStatus(serverUrls, asset, functionCode);
            if (sdsy==null || sdsy.functionState == Enums.FunctionState.ready) {
                continue;
            }

            ProcessorAndCoreData.AssetServerUrl assetUrl = serverUrls.assetUrl;

            final MetadataParamsYaml.Status functionDownloadStatus = metadataService.getFunctionDownloadStatuses(assetUrl, functionCode);
            if (functionDownloadStatus==null) {
                log.warn("#811.010 Function {} wasn't found on Dispatcher {}", functionCode, dispatcherUrl);
                continue;
            }
            if (functionDownloadStatus.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                log.warn("#811.010 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, dispatcherUrl);
                continue;
            }
            if (functionDownloadStatus.functionState != Enums.FunctionState.none) {
                log.warn("#811.013 Function {} from {} was already processed and has a state {}.", functionCode, dispatcherUrl, functionDownloadStatus.functionState);
                continue;
            }

            // task.functionConfig is null when we are downloading a function proactively, without any task
            TaskParamsYaml.FunctionConfig functionConfig = task.functionConfig;
            if (functionConfig ==null) {
                ProcessorFunctionService.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
                        processorFunctionService.downloadFunctionConfig(serverUrls.dispatcherUrl, asset, functionCode);
                if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.error) {
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.function_config_error);
                    continue;
                }
                if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.not_found) {
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.not_found);
                    continue;
                }
                functionConfig = downloadedFunctionConfigStatus.functionConfig;
            }
            MetadataService.ChecksumWithSignatureState state = metadataService.prepareChecksumWithSignature(dispatcher.signatureRequired, functionCode, assetUrl, functionConfig);
            if (state.state== Enums.SignatureStates.signature_not_valid) {
                continue;
            }

            final MetadataParamsYaml.ProcessorState dispatcherInfo = metadataService.dispatcherUrlAsCode(serverUrls.dispatcherUrl);
            final File baseResourceDir = dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherInfo);
            final AssetFile assetFile = AssetUtils.prepareFunctionFile(baseResourceDir, functionCode, functionConfig.file);

            switch (functionDownloadStatus.functionState) {
                case none:
                    if (assetFile.isError) {
                        metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.asset_error);
                        continue;
                    }
                    break;
                case ok:
                    log.error("#811.020 Unexpected state of function {}, dispatcher {}, will be resetted to none", functionCode, dispatcherUrl);
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.none);
                    break;
                case signature_wrong:
                case signature_not_found:
                case checksum_wrong:
                case not_supported_os:
                case asset_error:
                case download_error:
                case io_error:
                case dispatcher_config_error:
                    log.warn("#811.030 Function {} can't be downloaded from {}. The current status is {}", functionCode, dispatcherUrl, functionDownloadStatus.functionState);
                    continue;
                case function_config_error:
                    log.warn("#811.030 Config for function {} wasn't downloaded from {}, State will be reseted to none", functionCode, dispatcherUrl);
                    // reset to none for trying again
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.none);
                    continue;
                case not_found:
                    log.warn("#811.033 Config for function {} wasn't found on {}, State will be reseted to none", functionCode, dispatcherUrl);
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.none);
                    continue;
                case ready:
                    if (assetFile.isError || !assetFile.isContent ) {
                        log.warn("#811.040 Function {} from {} is broken. Set state to asset_error", functionCode, dispatcherUrl);
                        metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.asset_error);
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
                    final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);
                    try {

                        String chunkSize;
                        if (task.chunkSize != null) {
                            chunkSize = task.chunkSize.toString();
                        }
                        else {
                            log.warn("#811.043 task.chunkSize is null, 500k will be used as value");
                            chunkSize = "500000";
                        }

                        final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("code", task.functionCode)
                                .addParameter("chunkSize", chunkSize)
                                .addParameter("chunkNum", Integer.toString(idx));

                        final Request request = Request.Get(builder.build()).connectTimeout(5000).socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response = HttpClientExecutor.getExecutor(asset.url, asset.username, asset.password).execute(request);
                        File partFile = new File(dir, String.format(mask, idx));

                        final HttpResponse httpResponse = response.returnResponse();
                        int statusCode = httpResponse.getStatusLine().getStatusCode();
                        if (statusCode ==HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                            final String es = S.f("#811.047 Function %s can't be downloaded, asset srv %s was mis-configured, dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.error(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.dispatcher_config_error);
                            resourceState = Enums.FunctionState.dispatcher_config_error;
                            break;
                        }
                        else if (statusCode !=HttpStatus.OK.value()) {
                            final String es = S.f("#811.050 Function %s can't be downloaded from asset srv %s, dispatcher %s, status code: %d", task.functionCode, asset.url, dispatcherUrl, statusCode);
                            log.error(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }

                        try (final FileOutputStream out = new FileOutputStream(partFile)) {
                            final HttpEntity entity = httpResponse.getEntity();
                            if (entity != null) {
                                entity.writeTo(out);
                            }
                            else {
                                log.warn("#811.055 http entity is null");
                            }
                        }
                        final Header[] headers = httpResponse.getAllHeaders();
                        if (!DownloadUtils.isChunkConsistent(partFile, headers)) {
                            log.error("#811.060 error while downloading chunk of function {}, size is different", functionCode);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.download_error);
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
                        if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                            final String es = S.f("#811.065 Function %s can't be downloaded, asset srv %s was mis-configured, dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.dispatcher_config_error);
                            resourceState = Enums.FunctionState.dispatcher_config_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_BAD_GATEWAY ) {
                            final String es = String.format("#810.035 BAD_GATEWAY error while downloading " +
                                    "a function #%s on asset srv %s, dispatcher %s. will try later again", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            // do nothing and try later again
                            return;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            final String es = S.f("#811.070 Function %s wasn't found on asset srv %s for dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.not_found);
                            resourceState = Enums.FunctionState.not_found;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            final String es = S.f("#811.080 Unknown error with a function %s on asset srv %s, dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            final String es = S.f("#811.090 Unknown error with a resource %s on asset srv %s, dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                        else {
                            final String es = S.f("#811.091 Unknown error with a resource %s on asset srv %s, dispatcher %s", task.functionCode, asset.url, dispatcherUrl);
                            log.warn(es);
                            metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.download_error);
                            resourceState = Enums.FunctionState.download_error;
                            break;
                        }
                    }
                    // work around for handling a burst access to asset server
                    //noinspection BusyWait
                    Thread.sleep(200);
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.FunctionState.none) {
                    log.error("#811.100 something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.FunctionState.download_error || resourceState == Enums.FunctionState.not_found || resourceState == Enums.FunctionState.dispatcher_config_error) {
                    log.warn("#811.110 function {} can't be downloaded, state: {}", functionCode, resourceState);
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

                CheckSumAndSignatureStatus status = metadataService.getCheckSumAndSignatureStatus(assetUrl, dispatcherUrl, asset, functionCode, state, functionTempFile);
                if (status.checksum==CheckSumAndSignatureStatus.Status.correct && status.signature==CheckSumAndSignatureStatus.Status.correct) {
                    //noinspection ResultOfMethodCallIgnored
                    functionTempFile.renameTo(assetFile.file);
                    metadataService.setFunctionState(assetUrl, functionCode, Enums.FunctionState.ready);
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
            catch (ConnectException e) {
                log.error("#811.143 ConnectException: {}", e.toString());
            }
            catch (IOException e) {
                log.error("#811.150 IOException", e);
            }
            catch (URISyntaxException e) {
                log.error("#811.160 URISyntaxException", e);
            }
            catch (Throwable th) {
                log.error("#811.165 Throwable", th);
            }
        }
    }

    public void prepareFunctionForDownloading() {
        metadataService.getStatuses().forEach(o -> {
            if (o.sourcing== EnumsApi.FunctionSourcing.dispatcher && (o.functionState == Enums.FunctionState.none || !o.verified)) {
                ProcessorAndCoreData.AssetServerUrl assetServerUrl = new ProcessorAndCoreData.AssetServerUrl(o.assetUrl);

                final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                        dispatcherLookupExtendedService.lookupExtendedMap.get(o.assetUrl);

                DispatcherContextInfo contextInfo = dispatcherContextService.getCtx(assetServerUrl);

                if (contextInfo==null) {
                    log.info("#811.190 dispatcher contextInfo wasn't found, assetUrl: {}", o.assetUrl);
                    return;
                }

                if (contextInfo.chunkSize==null) {
                    log.info("#811.195 (dispatcher.config.chunkSize==null), dispatcherUrl: {}", o.assetUrl);
                    return;
                }

                log.info("Create new DownloadFunctionTask for downloading function {} from {}, chunk size: {}",
                        o.code, o.assetUrl, contextInfo.chunkSize);

                DownloadFunctionTask functionTask = new DownloadFunctionTask(contextInfo.chunkSize, o.code, assetServerUrl, null, o.assetUrl);
                functionTask.dispatcher = dispatcher.dispatcherLookup;

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