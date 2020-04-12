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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.EnumsApi;
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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DownloadVariableService extends AbstractTaskQueue<DownloadVariableTask> {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;

    @SuppressWarnings("Duplicates")
    public void fixedDelay() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        DownloadVariableTask task;
        while ((task = poll()) != null) {
            EnumsApi.DataType type;
            String es;
            switch(task.context) {
                case global:
                    type = EnumsApi.DataType.global_variable;
                    break;
                case local:
                    type = EnumsApi.DataType.variable;
                    break;
                case array:
                    es = "#810.005 Array type of variable isn't supported right now, variableId: " + task.variableId;
                    log.error(es);
                    processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.taskId, es);
                    continue;
                default:
                    es = "#810.007 Unknown context: " + task.context+ ", variableId: " +  task.variableId;
                    log.error(es);
                    processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.taskId, es);
                    continue;
            }
            ProcessorTask processorTask = processorTaskService.findById(task.dispatcher.url, task.taskId);
            if (processorTask !=null && processorTask.finishedOn!=null) {
                log.info("Task #{} was already finished, skip it", task.taskId);
                continue;
            }
            AssetFile assetFile = AssetUtils.prepareFileForVariable(task.targetDir, task.variableId.toString(), null, type);
            if (assetFile.isError ) {
                log.warn("#810.010 Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.debug("Resource was already downloaded. Asset file: {}", assetFile);
                continue;
            }

            log.info("Start processing the download task {}", task);
            try {
                final String payloadRestUrl = task.dispatcher.url + "/rest/v1/payload/resource/"+type;
                final String uri = payloadRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' +
                        task.processorId + '-' + task.taskId + '-' + URLEncoder.encode(task.variableId.toString(), StandardCharsets.UTF_8.toString());

                File parentDir = assetFile.file.getParentFile();
                if (parentDir==null) {
                    es = "#810.020 Can't get parent dir for asset file " + assetFile.file.getAbsolutePath();
                    log.error(es);
                    processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.taskId, es);
                    continue;
                }
                File tempFile;
                try {
                    tempFile = File.createTempFile("resource-", ".temp", parentDir);
                } catch (IOException e) {
                    es = "#810.025 Error creating temp file in parent dir: " + parentDir.getAbsolutePath();
                    log.error(es, e);
                    processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.taskId, es);
                    continue;
                }

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.ResourceState resourceState = Enums.ResourceState.none;
                int idx = 0;
                do {
                    try {
                        final URIBuilder builder = new URIBuilder(uri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("id", task.variableId.toString())
                                .addParameter("chunkSize", task.chunkSize!=null ? task.chunkSize.toString() : "")
                                .addParameter("chunkNum", Integer.toString(idx));

                        final URI build = builder.build();
                        final Request request = Request.Get(build)
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response = HttpClientExecutor.getExecutor(
                                task.dispatcher.url, task.dispatcher.restUsername, task.dispatcher.restPassword).execute(request);
                        File partFile = new File(dir, String.format(mask, idx));
                        final HttpResponse httpResponse = response.returnResponse();
                        if (httpResponse.getStatusLine().getStatusCode()==HttpServletResponse.SC_GONE) {
                            resourceState = setVariableWasntFound(task);
                            break;
                        }
                        try (final FileOutputStream out = new FileOutputStream(partFile)) {
                            final HttpEntity entity = httpResponse.getEntity();
                            if (entity != null) {
                                entity.writeTo(out);
                            }
                            else {
                                log.warn("#810.031 http entity is null");
                            }
                        }
                        final Header[] headers = httpResponse.getAllHeaders();
                        if (!DownloadUtils.isChunkConsistent(partFile, headers)) {
                            log.error("#810.032 error while downloading chunk of resource {}, size is different", assetFile.file.getPath());
                            resourceState = Enums.ResourceState.transmitting_error;
                            break;
                        }
                        if (DownloadUtils.isLastChunk(headers)) {
                            resourceState = Enums.ResourceState.ok;
                            break;
                        }
                        if (partFile.length()==0) {
                            resourceState = Enums.ResourceState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            resourceState = setVariableWasntFound(task);
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            es = String.format("#810.036 Unknown error with a resource %s. Task #%s is finished.", task.variableId, task.getTaskId());
                            log.warn(es);
                            processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.getTaskId(), es);
                            resourceState = Enums.ResourceState.unknown_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            es = String.format("#810.037 Unknown error with a resource %s. Task #%s is finished.", task.variableId, task.getTaskId());
                            log.warn(es);
                            processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.getTaskId(), es);
                            resourceState = Enums.ResourceState.unknown_error;
                            break;
                        }
                    }
                    catch(SocketTimeoutException e) {
                        log.error("#810.040 SocketTimeoutException, uri: " + uri, e);
                        return;
                    }
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.ResourceState.none) {
                    log.error("#810.050 something wrong, is file too big or chunkSize too small? chunkSize: {}", task.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.ResourceState.unknown_error || resourceState == Enums.ResourceState.resource_doesnt_exist) {
                    log.warn("#810.053 Variable {} can't be acquired, state: {}", assetFile.file.getPath(), resourceState);
                    continue;
                }
                else if (resourceState == Enums.ResourceState.transmitting_error) {
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    for (int i = 0; i <= idx; i++) {
                        final File input = new File(assetFile.file.getAbsolutePath() + "." + i + ".tmp");
                        if (input.length()==0) {
                            continue;
                        }
                        FileUtils.copyFile(input, fos);
                    }
                }

                if (!tempFile.renameTo(assetFile.file)) {
                    log.warn("#810.060 Can't rename file {} to file {}", tempFile.getPath(), assetFile.file.getPath());
                    continue;
                }
                log.info("Resource #{} was loaded", task.variableId);
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpServletResponse.SC_CONFLICT) {
                    log.warn("#810.080 Variable with id {} is broken and need to be recreated", task.variableId);
                } else {
                    log.error("#810.090 HttpResponseException.getStatusCode(): {}", e.getStatusCode());
                    log.error("#810.091 HttpResponseException", e);
                }
            } catch (SocketTimeoutException e) {
                log.error("#810.100 SocketTimeoutException", e);
            } catch (IOException e) {
                log.error("#810.110 IOException", e);
            } catch (URISyntaxException e) {
                log.error("#810.120 URISyntaxException", e);
            }
        }
    }

    @NonNull
    public Enums.ResourceState setVariableWasntFound(DownloadVariableTask task) {
        String es;
        Enums.ResourceState resourceState;
        es = String.format("#810.027 Variable %s wasn't found on dispatcher. Set state of ask #%s to 'finished'.", task.variableId, task.getTaskId());
        log.warn(es);
        processorTaskService.markAsFinishedWithError(task.dispatcher.url, task.getTaskId(), es);
        resourceState = Enums.ResourceState.resource_doesnt_exist;
        return resourceState;
    }
}