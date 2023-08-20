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
package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.DispatcherContextInfoHolder;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.function.ChecksumAndSignatureService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.tasks.DownloadFunctionTask;
import ai.metaheuristic.ai.processor.tasks.GetDispatcherContextInfoTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DownloadFunctionService extends AbstractTaskQueue<DownloadFunctionTask> implements QueueProcessor {

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;
    private final ChecksumAndSignatureService checksumAndSignatureService;
    private final GetDispatcherContextInfoService getDispatcherContextInfoService;

    @SuppressWarnings("Duplicates")
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        DownloadFunctionTask task;
        while((task = poll())!=null) {
            final String functionCode = task.functionCode;

            final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = task.assetManagerUrl;
            final DispatcherLookupParamsYaml.AssetManager assetManager = processorEnvironment.dispatcherLookupExtendedService.getAssetManager(assetManagerUrl);
            if (assetManager==null) {
                log.error("#811.007 assetManager server wasn't found for url {}", assetManagerUrl.url);
                continue;
            }

            final DispatcherData.DispatcherContextInfo contextInfo = DispatcherContextInfoHolder.getCtx(assetManagerUrl);

            // process only if dispatcher has already sent its config
            if (contextInfo==null || contextInfo.chunkSize==null) {
                log.warn("#951.120 Asset dispatcher {} wasn't initialized yet, chunkSize is  undefined", assetManagerUrl.url);
                getDispatcherContextInfoService.add(new GetDispatcherContextInfoTask(assetManagerUrl));
                continue;
            }

            MetadataParams.FunctionConfigAndStatus functionConfigAndStatus = processorEnvironment.metadataParams.syncFunctionStatus(assetManagerUrl, assetManager, functionCode);
            if (functionConfigAndStatus==null) {
                continue;
            }

            MetadataParamsYaml.Function functionDownloadStatus = functionConfigAndStatus.status;
            if (functionDownloadStatus==null) {
                continue;
            }

            if (!functionDownloadStatus.state.needVerification) {
                log.warn("#811.013 Function {} from {} was already processed and has a state {}.", functionCode, assetManager.url, functionDownloadStatus.state);
                continue;
            }

            final AssetFile assetFile = functionConfigAndStatus.assetFile;
            if (assetFile==null) {
                throw new IllegalStateException("(assetFile==null)");
            }
            TaskParamsYaml.FunctionConfig functionConfig = functionConfigAndStatus.functionConfig;
            if (functionConfig == null) {
                throw new IllegalStateException("(functionConfig == null)");
            }

            if (functionDownloadStatus.state.needDownload) {
                try {

                    File functionTempFile = new File(assetFile.file.toAbsolutePath() + ".tmp");

                    final String targetUrl = assetManager.url + Consts.REST_ASSET_URL + "/function";

                    String mask = assetFile.file.getFileName().toString() + ".%s.tmp";
                    File dir = assetFile.file.toFile().getParentFile();
                    EnumsApi.FunctionState functionState = EnumsApi.FunctionState.none;
                    int idx = 0;
                    do {
                        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);
                        try {

                            final URIBuilder builder = new URIBuilder(targetUrl + randomPartUri).setCharset(StandardCharsets.UTF_8)
                                    .addParameter("code", task.functionCode)
                                    .addParameter("chunkSize", contextInfo.chunkSize.toString())
                                    .addParameter("chunkNum", Integer.toString(idx));

                            final Request request = Request.get(builder.build()).connectTimeout(Timeout.ofSeconds(5));
                                    //.socketTimeout(20000);

                            RestUtils.addHeaders(request);

                            Response response = HttpClientExecutor.getExecutor(assetManager.url, assetManager.username, assetManager.password).execute(request);
                            File partFile = new File(dir, String.format(mask, idx));

                            final HttpResponse httpResponse = response.returnResponse();
                            if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
                                throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
                            }
                            final int statusCode = classicHttpResponse.getCode();
                            if (statusCode == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                                final String es = S.f("#811.047 Function %s can't be downloaded, assetManager manager %s was mis-configure. Reason: Current dispatcher is configured with assetMode==replicated, but you're trying to use it as the source for downloading of functions", task.functionCode, assetManager.url);
                                log.error(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error);
                                functionState = EnumsApi.FunctionState.dispatcher_config_error;
                                break;
                            }
                            else if (statusCode == HttpStatus.GONE.value()) {
                                final String es = S.f("#811.048 Function %s was deleted at assetManager manager %s.", task.functionCode, assetManager.url);
                                log.error(es);
                                // do not delete this function code because it can be received from dispatcher, so it'll be created constantly, if deleted
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found);
                                functionState = EnumsApi.FunctionState.not_found;
                                break;
                            }
                            else if (statusCode != HttpStatus.OK.value()) {
                                final String es = S.f("#811.050 Function %s can't be downloaded from assetManager manager %s, status code: %d", task.functionCode, assetManager.url, statusCode);
                                log.error(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                                functionState = EnumsApi.FunctionState.download_error;
                                break;
                            }

                            try (final FileOutputStream out = new FileOutputStream(partFile)) {
                                final HttpEntity entity = classicHttpResponse.getEntity();
                                if (entity != null) {
                                    entity.writeTo(out);
                                } else {
                                    log.warn("811.055 http entity is null");
                                }
                            }
                            final Header[] headers = httpResponse.getHeaders();
                            if (!DownloadUtils.isChunkConsistent(partFile, headers)) {
                                log.error("811.060 error while downloading chunk of function {}, size is different", functionCode);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                                functionState = EnumsApi.FunctionState.download_error;
                                break;
                            }
                            if (DownloadUtils.isLastChunk(headers)) {
                                functionState = EnumsApi.FunctionState.ok;
                                break;
                            }
                            if (partFile.length() == 0) {
                                functionState = EnumsApi.FunctionState.ok;
                                break;
                            }
                        } catch (HttpResponseException e) {
                            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                                final String es = S.f("#811.065 Function %s can't be downloaded, assetManager manager %s was mis-configured", task.functionCode, assetManager.url);
                                log.warn(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error);
                                functionState = EnumsApi.FunctionState.dispatcher_config_error;
                                break;
                            } else if (e.getStatusCode() == HttpServletResponse.SC_BAD_GATEWAY) {
                                final String es = String.format("#810.035 BAD_GATEWAY error while downloading " +
                                        "a function #%s on assetManager srv %s. will try later again", task.functionCode, assetManager.url);
                                log.warn(es);
                                // do nothing and try later again
                                return;
                            } else if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                                final String es = S.f("#811.070 Function %s wasn't found on assetManager manager %s", task.functionCode, assetManager.url);
                                log.warn(es);
                                // do not delete this function code because it can be received from dispatcher, so it'll be created constantly, if deleted
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found);
                                functionState = EnumsApi.FunctionState.not_found;
                                break;
                            } else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
                                final String es = S.f("#811.080 Unknown error with a function %s on assetManager manager %s", task.functionCode, assetManager.url);
                                log.warn(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                                functionState = EnumsApi.FunctionState.download_error;
                                break;
                            } else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                                final String es = S.f("#811.090 Unknown error with a resource %s on assetManager manager %s", task.functionCode, assetManager.url);
                                log.warn(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                                functionState = EnumsApi.FunctionState.download_error;
                                break;
                            } else {
                                final String es = S.f("#811.091 Unknown error with a resource %s on assetManager manager %s, dispatcher %s", task.functionCode, assetManager.url);
                                log.warn(es);
                                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                                functionState = EnumsApi.FunctionState.download_error;
                                break;
                            }
                        }
                        // work around for handling a burst access to assetManager server
                        //noinspection BusyWait
                        Thread.sleep(50);
                        idx++;
                    } while (idx < 1000);
                    if (functionState == EnumsApi.FunctionState.none) {
                        log.error("#811.100 something wrong, is file too big or chunkSize too small? chunkSize: {}", contextInfo.chunkSize);
                        continue;
                    }
                    else if (functionState == EnumsApi.FunctionState.download_error) {
                        log.warn("#811.110 function {} will be downloaded later, state: {}", functionCode, functionState);
                        processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error);
                        continue;
                    }
                    else if (functionState == EnumsApi.FunctionState.dispatcher_config_error) {
                        log.warn("#811.111 function {} can't be downloaded, state: {}", functionCode, functionState);
                        processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error);
                        continue;
                    }
                    else if (functionState == EnumsApi.FunctionState.not_found) {
                        log.warn("#811.112 function {} can't be downloaded, state: {}", functionCode, functionState);
                        processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found);
                        continue;
                    }

                    DownloadUtils.combineParts(assetFile, functionTempFile, idx);

                    try {
                        Files.move(functionTempFile.toPath(), assetFile.file);
                    }
                    catch (IOException e) {
                        log.warn("#811.138 Can't rename file {} to file {}", functionTempFile.getPath(), assetFile.file);
                        return;
                    }
                } catch (HttpResponseException e) {
                    logError(functionCode, e);
                } catch (SocketTimeoutException e) {
                    log.error("#811.140 SocketTimeoutException: {}", e.toString());
                } catch (ConnectException e) {
                    log.error("#811.143 ConnectException: {}", e.toString());
                } catch (IOException e) {
                    log.error("#811.150 IOException", e);
                } catch (URISyntaxException e) {
                    log.error("#811.160 URISyntaxException", e);
                } catch (Throwable th) {
                    log.error("#811.165 Throwable", th);
                }
            }
            if (Files.notExists(assetFile.file)) {
                log.error("#811.180 assetManager file {} is missing", assetFile.file.toAbsolutePath());
                continue;
            }
            ChecksumAndSignatureData.ChecksumWithSignatureInfo state = MetadataParams.prepareChecksumWithSignature(functionConfigAndStatus.functionConfig);

            CheckSumAndSignatureStatus status;
            try {
                status = checksumAndSignatureService.getCheckSumAndSignatureStatus(assetManagerUrl, assetManager, functionCode, state, assetFile.file);
            } catch (IOException e) {
                log.error("#811.185 Error in getCheckSumAndSignatureStatus(),functionCode: {},  assetManager file {}, error: {}",
                        functionCode, assetFile.getFile().toAbsolutePath(), e.toString());
                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.io_error);
                continue;
            }

            if (status.checksum != EnumsApi.ChecksumState.wrong && status.signature != EnumsApi.SignatureState.wrong) {
                processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ready);
            } else {
                if (status.checksum== EnumsApi.ChecksumState.wrong) {
                    processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.checksum_wrong);
                }
                else {
                    processorEnvironment.metadataParams.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.signature_wrong);
                }
                try {
                    Files.deleteIfExists(assetFile.file);
                }
                catch (IOException e) {
                    //
                }
            }
        }
    }

    public void prepareFunctionForDownloading() {
        processorEnvironment.metadataParams.getStatuses().forEach(o -> {
            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(o.assetManagerUrl);
            if (o.sourcing== EnumsApi.FunctionSourcing.dispatcher && o.state.needVerification) {
                final DispatcherLookupParamsYaml.AssetManager asset = processorEnvironment.dispatcherLookupExtendedService.getAssetManager(assetManagerUrl);
                if (asset==null || asset.disabled) {
                    return;
                }

                if (System.currentTimeMillis() - o.lastCheck < o.state.recheckPeriod) {
                    log.debug("#811.1854 Function {} from {} is in cool-down mode", o.code, o.assetManagerUrl);
                    return;
                }

                DispatcherData.DispatcherContextInfo contextInfo = DispatcherContextInfoHolder.getCtx(assetManagerUrl);

                if (contextInfo==null) {
                    log.info("#811.190 contextInfo for asset manager {} wasn't found, function: {}", o.assetManagerUrl, o.code);
                    getDispatcherContextInfoService.add(new GetDispatcherContextInfoTask(assetManagerUrl));
                    return;
                }

                if (contextInfo.chunkSize==null) {
                    log.info("#811.195 (dispatcher.config.chunkSize==null), dispatcherUrl: {}", o.assetManagerUrl);
                    return;
                }

                log.info("Create new DownloadFunctionTask for downloading function {} from {}, chunk size: {}",
                        o.code, o.assetManagerUrl, contextInfo.chunkSize);

                ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl(o.assetManagerUrl);
                final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                        processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

                boolean signatureRequired = dispatcher==null ? (asset.publicKey != null) : dispatcher.dispatcherLookup.signatureRequired;

                DownloadFunctionTask functionTask = new DownloadFunctionTask(o.code, assetManagerUrl, signatureRequired);
                add(functionTask);
            }
            else if (o.sourcing== EnumsApi.FunctionSourcing.processor) {
                processorEnvironment.metadataParams.setFunctionFromProcessorAsReady(assetManagerUrl, o.code);
            }
        });
    }

    private static void logError(String functionCode, HttpResponseException e) {
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