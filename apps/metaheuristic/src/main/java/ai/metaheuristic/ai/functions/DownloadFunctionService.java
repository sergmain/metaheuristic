/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority;
import ai.metaheuristic.ai.processor.DispatcherContextInfoHolder;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.actors.DownloadUtils;
import ai.metaheuristic.ai.processor.actors.GetDispatcherContextInfoService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.tasks.GetDispatcherContextInfoTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.api.data.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.ArtifactCommonUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
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
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static ai.metaheuristic.ai.functions.FunctionRepositoryData.*;

@SuppressWarnings("unused")
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DownloadFunctionService {

    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;
    private final GetDispatcherContextInfoService getDispatcherContextInfoService;
    private final FunctionRepositoryProcessorService functionRepositoryProcessorService;

    private final MultiTenantedQueue<DownloadPriority, DownloadFunctionTask> downloadFunctionQueue =
        new MultiTenantedQueue<>(100, Duration.ZERO, true, "download-function-", this::downloadFunction);

    public void addTask(DownloadFunctionTask task) {
        downloadFunctionQueue.putToQueue(task);
    }

    @Async
    @EventListener
    public void processAssetPreparing(FunctionRepositoryData.DownloadFunctionTask event) {
        try {
            addTask(event);
        } catch (Throwable th) {
            log.error("811.002 Error, need to investigate ", th);
        }
    }

    public void downloadFunction(DownloadFunctionTask task) {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        if (task.shortFunctionConfig.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
            log.warn("811.005 Attempt to download function from dispatcher but sourcing is" + task.shortFunctionConfig.sourcing);
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
            log.error("811.007 assetManager server wasn't found for url {}", assetManagerUrl.url);
            return;
        }

        final DispatcherData.DispatcherContextInfo contextInfo = getDispatcherContextInfo(assetManagerUrl);
        if (contextInfo == null) {
            return;
        }

        DownloadedFunctionConfigStatus status = ProcessorFunctionUtils.downloadFunctionConfig(assetManager, functionCode);
        Path baseFunctionDir = MetadataParams.prepareBaseDir(globals.processorResourcesPath, assetManagerUrl);

        final String actualFunctionFile = AssetUtils.getActualFunctionFile(status.functionConfig);
        if (actualFunctionFile==null) {
            log.error("811.010 actualFunctionFile is null");
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error, null);
            return;
        }
        final AssetFile assetFile = AssetUtils.prepareFunctionAssetFile(baseFunctionDir, functionCode, actualFunctionFile);
        if (assetFile.isError) {
            log.error("811. 015 AssetFile error creation for function " + functionCode + " encountered");
            return;
        }

        String functionZipFilename = ArtifactCommonUtils.normalizeCode(task.functionCode) + CommonConsts.ZIP_EXT;
        String mask = functionZipFilename + ".%s.tmp";
//        Path parentDir = assetFile.file.getParent();
        Path parentDir = DirUtils.getParent(assetFile.file, Path.of(actualFunctionFile));
        if (parentDir==null) {
            log.error("811.070 parentDir is null");
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error, assetFile);
            return;
        }

        Path downloadDir = parentDir.getParent().resolve(parentDir.getFileName().toString()+"-download");
        Path functionZip = downloadDir.resolve(functionZipFilename);

        ChecksumAndSignatureData.ChecksumWithSignatureInfo checksum = MetadataParams.prepareChecksumWithSignature(status.functionConfig.checksumMap);

        if (assetFile.isContent) {
            try {
                CheckSumAndSignatureStatus checkSumAndSignatureStatus = ChecksumAndSignatureUtils.getCheckSumAndSignatureStatus(assetManagerUrl, assetManager, functionCode, checksum, functionZip);
                FunctionRepositoryProcessorService.setChecksumAndSignatureStatus(assetManagerUrl, functionCode, checkSumAndSignatureStatus);
                if (checkSumAndSignatureStatus.checksum != EnumsApi.ChecksumState.wrong && checkSumAndSignatureStatus.signature != EnumsApi.SignatureState.wrong) {
                    log.info("Previous instance of function {} was found, checksum: {}, signature: {}", functionCode, checkSumAndSignatureStatus.checksum, checkSumAndSignatureStatus.signature);
                    FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ready, assetFile);
                    return;
                }

            } catch (IOException e) {
                log.warn("Checksum verification for function {} was failed with an error: {}", functionCode, e.getMessage());
            }
            try {
                Files.deleteIfExists(assetFile.file);
            }
            catch (IOException e) {
                //
            }
        }

        DownloadStatus functionDownloadStatus = FunctionRepositoryProcessorService.getFunctionDownloadStatus(assetManagerUrl, functionCode);
        if (functionDownloadStatus!=null) {
            return;
        }

        try {
            Files.createDirectories(downloadDir);
            Files.createDirectories(parentDir);

            EnumsApi.FunctionState functionState = EnumsApi.FunctionState.none;
            int idx = 0;
            final String targetUrl = assetManager.url + Consts.REST_ASSET_URL + "/function";
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
                    Path partFile = downloadDir.resolve(String.format(mask, idx));

                    final HttpResponse httpResponse = response.returnResponse();
                    if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
                        throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
                    }
                    final int statusCode = classicHttpResponse.getCode();
                    if (statusCode == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                        final String es = S.f("811.047 Function %s can't be downloaded, assetManager manager %s was mis-configure. Reason: Current dispatcher is configured with assetMode==replicated, but you're trying to use it as the source for downloading of functions", task.functionCode, assetManager.url);
                        log.error(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error, assetFile);
                        functionState = EnumsApi.FunctionState.dispatcher_config_error;
                        break;
                    }
                    else if (statusCode == HttpStatus.GONE.value()) {
                        final String es = S.f("811.048 Function %s was deleted at assetManager manager %s.", task.functionCode, assetManager.url);
                        log.error(es);
                        // do not delete this function code because it can be received from dispatcher, so it'll be created constantly, if deleted
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found, assetFile);
                        functionState = EnumsApi.FunctionState.not_found;
                        break;
                    }
                    else if (statusCode != HttpStatus.OK.value()) {
                        final String es = S.f("811.050 Function %s can't be downloaded from assetManager manager %s, checkSumAndSignatureStatus code: %d", task.functionCode, assetManager.url, statusCode);
                        log.error(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                        functionState = EnumsApi.FunctionState.download_error;
                        break;
                    }

                    try (final OutputStream out = Files.newOutputStream(partFile)) {
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
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                        functionState = EnumsApi.FunctionState.download_error;
                        break;
                    }
                    if (DownloadUtils.isLastChunk(headers)) {
                        functionState = EnumsApi.FunctionState.ok;
                        break;
                    }
                    if (Files.size(partFile)==0) {
                        functionState = EnumsApi.FunctionState.ok;
                        break;
                    }
                } catch (HttpResponseException e) {
                    if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                        final String es = S.f("811.065 Function %s can't be downloaded, assetManager manager %s was mis-configured", task.functionCode, assetManager.url);
                        log.warn(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error, assetFile);
                        functionState = EnumsApi.FunctionState.dispatcher_config_error;
                        break;
                    } else if (e.getStatusCode() == HttpServletResponse.SC_BAD_GATEWAY) {
                        final String es = String.format("811.035 BAD_GATEWAY error while downloading " +
                                "a function #%s on assetManager srv %s. will try later again", task.functionCode, assetManager.url);
                        log.warn(es);
                        // do nothing and try later again
                        return;
                    } else if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                        final String es = S.f("811.070 Function %s wasn't found on assetManager manager %s", task.functionCode, assetManager.url);
                        log.warn(es);
                        // do not delete this function code because it can be received from dispatcher, so it'll be created constantly, if deleted
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found, assetFile);
                        functionState = EnumsApi.FunctionState.not_found;
                        break;
                    } else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
                        final String es = S.f("811.080 Unknown error with a function %s on assetManager manager %s", task.functionCode, assetManager.url);
                        log.warn(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                        functionState = EnumsApi.FunctionState.download_error;
                        break;
                    } else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                        final String es = S.f("811.090 Unknown error with a resource %s on assetManager manager %s", task.functionCode, assetManager.url);
                        log.warn(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                        functionState = EnumsApi.FunctionState.download_error;
                        break;
                    } else {
                        final String es = S.f("811.091 Unknown error with a resource %s on assetManager manager %s, dispatcher %s", task.functionCode, assetManager.url);
                        log.warn(es);
                        FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                        functionState = EnumsApi.FunctionState.download_error;
                        break;
                    }
                }
                // work around for handling a burst access to assetManager server
                //noinspection BusyWait
                //Thread.sleep(50);
                idx++;
            } while (idx < 1000);
            if (functionState == EnumsApi.FunctionState.none) {
                log.error("811.100 something wrong, is file too big or chunkSize too small? chunkSize: {}", contextInfo.chunkSize);
                return;
            }
            else if (functionState == EnumsApi.FunctionState.download_error) {
                log.warn("811.110 function {} will be downloaded later, state: {}", functionCode, functionState);
                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.download_error, assetFile);
                return;
            }
            else if (functionState == EnumsApi.FunctionState.dispatcher_config_error) {
                log.warn("811.111 function {} can't be downloaded, state: {}", functionCode, functionState);
                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.dispatcher_config_error, assetFile);
                return;
            }
            else if (functionState == EnumsApi.FunctionState.not_found) {
                log.warn("811.112 function {} can't be downloaded, state: {}", functionCode, functionState);
                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.not_found, assetFile);
                return;
            }

            DownloadUtils.combineParts(functionZip, functionZip, idx);
            //ZipUtils.unzipFolder(functionZip, parentDir);

            int i=0;
        } catch (HttpResponseException e) {
            logError(functionCode, e);
        } catch (SocketTimeoutException e) {
            log.error("811.140 SocketTimeoutException: {}", e.toString());
        } catch (ConnectException e) {
            log.error("811.143 ConnectException: {}", e.toString());
        } catch (IOException e) {
            log.error("811.150 IOException", e);
        } catch (URISyntaxException e) {
            log.error("811.160 URISyntaxException", e);
        } catch (Throwable th) {
            log.error("811.165 Throwable", th);
        }
        CheckSumAndSignatureStatus checkSumAndSignatureStatus;
        try {
            checkSumAndSignatureStatus = ChecksumAndSignatureUtils.getCheckSumAndSignatureStatus(assetManagerUrl, assetManager, functionCode, checksum, functionZip);
            FunctionRepositoryProcessorService.setChecksumAndSignatureStatus(assetManagerUrl, functionCode, checkSumAndSignatureStatus);
        } catch (IOException e) {
            log.error("811.185 Error in getCheckSumAndSignatureStatus(),functionCode: {},  assetManager file {}, error: {}",
                    functionCode, assetFile.getFile().toAbsolutePath(), e.toString());
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.io_error, assetFile);
            return;
        }

        if (checkSumAndSignatureStatus.checksum != EnumsApi.ChecksumState.wrong && checkSumAndSignatureStatus.signature != EnumsApi.SignatureState.wrong) {
            ZipUtils.unzipFolder(functionZip, parentDir);
            if (Files.notExists(assetFile.file)) {
                log.error("811.180 assetManager file {} is missing", assetFile.file.toAbsolutePath());
                return;
            }
            FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ready, assetFile);
        } else {
            if (checkSumAndSignatureStatus.checksum== EnumsApi.ChecksumState.wrong) {
                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.checksum_wrong, assetFile);
            }
            else {
                FunctionRepositoryProcessorService.setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.signature_wrong, assetFile);
            }
            try {
                Files.deleteIfExists(assetFile.file);
            }
            catch (IOException e) {
                //
            }
        }
    }

    @Nullable
    private DispatcherData.DispatcherContextInfo getDispatcherContextInfo(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        final DispatcherData.DispatcherContextInfo contextInfo = DispatcherContextInfoHolder.getCtx(assetManagerUrl);

        // process only if dispatcher has already sent its config
        if (contextInfo==null || contextInfo.chunkSize==null) {
            log.warn("811.009 Asset dispatcher {} wasn't initialized yet, chunkSize is  undefined", assetManagerUrl.url);
            getDispatcherContextInfoService.add(new GetDispatcherContextInfoTask(assetManagerUrl));
            return null;
        }
        return contextInfo;
    }

    private static void logError(String functionCode, HttpResponseException e) {
        if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
            log.warn("811.200 Function with code {} wasn't found", functionCode);
        }
        else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
            log.warn("811.210 Function with code {} is broken and need to be recreated", functionCode);
        }
        else {
            log.error("811.220 HttpResponseException", e);
        }
    }

}