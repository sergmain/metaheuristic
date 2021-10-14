/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.*;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DownloadVariableService extends AbstractTaskQueue<DownloadVariableTask> implements QueueProcessor {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;

    @SuppressWarnings("Duplicates")
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
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
                    type = EnumsApi.DataType.variable;
                    log.debug("Start downloading array variable with variableId: " +  task.variableId);
                    break;
                default:
                    es = "#810.007 Unknown context: " + task.context+ ", variableId: " +  task.variableId;
                    log.error(es);
                    processorTaskService.markAsFinishedWithError(task.ref, task.taskId, es);
                    continue;
            }
            DispatcherData.DispatcherContextInfo dispatcherContextInfo = DispatcherContextInfoHolder.getCtx(task.ref.dispatcherUrl);
            if (dispatcherContextInfo==null || dispatcherContextInfo.chunkSize==null) {
                log.info("DispatcherContextInfo isn't inited for dispatcherUrl {}", task.ref.dispatcherUrl.url);
                continue;
            }
            ProcessorTask processorTask = processorTaskService.findById(task.ref, task.taskId);
            if (processorTask==null) {
                log.info("#810.008 Task #{} wasn't found, skip it", task.taskId);
                continue;
            }
            if (processorTask.finishedOn!=null) {
                log.info("#810.009 Task #{} was already finished, skip it", task.taskId);
                continue;
            }
            EnumsApi.ExecContextState state = currentExecState.getState(task.ref.dispatcherUrl, processorTask.execContextId);
            if (state!= EnumsApi.ExecContextState.STARTED && state!= EnumsApi.ExecContextState.PRODUCING) {
                log.info("ExecContext #{} is stopped, delete task #{}", processorTask.execContextId, task.taskId);
                processorTaskService.delete(task.ref, task.taskId);
                continue;
            }

            AssetFile assetFile = AssetUtils.prepareFileForVariable(task.targetDir, task.variableId, null, type);
            if (assetFile.isError ) {
                log.warn("#810.010 Resource can't be downloaded. Asset file initialization was failed, {}", assetFile);
                continue;
            }
            if (assetFile.isContent ) {
                log.debug("Resource was already downloaded. Asset file: {}", assetFile);
                continue;
            }

            log.debug("Start processing the download task {}", task);
            try {
                final String uri = task.dispatcher.url + "/rest/v1/payload/resource/"+type+'/'+task.taskId+'/'+
                        UUID.randomUUID().toString().substring(0, 8) + '-' +task.ref.processorId + '-' + task.taskId + '-' + URLEncoder.encode(task.variableId, StandardCharsets.UTF_8.toString());

                File parentDir = assetFile.file.getParentFile();
                if (parentDir==null) {
                    es = "#810.020 Can't get parent dir for asset file " + assetFile.file.getAbsolutePath();
                    log.error(es);
                    processorTaskService.markAsFinishedWithError(task.ref, task.taskId, es);
                    continue;
                }
                File tempFile;
                try {
                    tempFile = File.createTempFile("resource-", ".temp", parentDir);
                } catch (IOException e) {
                    es = "#810.025 Error creating temp file in parent dir: " + parentDir.getAbsolutePath();
                    log.error(es, e);
                    processorTaskService.markAsFinishedWithError(task.ref, task.taskId, es);
                    continue;
                }

                String mask = assetFile.file.getName() + ".%s.tmp";
                File dir = assetFile.file.getParentFile();
                Enums.VariableState resourceState = Enums.VariableState.none;
                int idx = 0;
                do {
                    try {
                        final URIBuilder builder = new URIBuilder(uri).setCharset(StandardCharsets.UTF_8)
                                .addParameter("id", task.variableId)
                                .addParameter("chunkSize", dispatcherContextInfo.chunkSize.toString())
                                .addParameter("chunkNum", Integer.toString(idx));

                        final URI build = builder.build();
                        final Request request = Request.Get(build)
                                .connectTimeout(5000)
                                .socketTimeout(20000);

                        RestUtils.addHeaders(request);

                        Response response = HttpClientExecutor.getExecutor(
                                task.ref.dispatcherUrl.url, task.dispatcher.restUsername, task.dispatcher.restPassword).execute(request);
                        File partFile = new File(dir, String.format(mask, idx));
                        final HttpResponse httpResponse = response.returnResponse();
                        if (httpResponse.getStatusLine().getStatusCode()==HttpServletResponse.SC_NO_CONTENT) {
                            if (task.nullable) {
                                processorTaskService.setInputAsEmpty(task.ref, task.taskId, task.variableId);
                                resourceState = Enums.VariableState.ok;
                            }
                            else {
                                es = String.format("#810.027 Dispatcher reported that variable #%s is empty but configuration states nullable==false. " +
                                        "Task #%s is finished with error.", task.variableId, task.getTaskId());
                                log.warn(es);
                                processorTaskService.markAsFinishedWithError(task.ref, task.getTaskId(), es);
                                resourceState = Enums.VariableState.variable_cant_be_null;
                            }
                            break;
                        }
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
                            resourceState = Enums.VariableState.transmitting_error;
                            break;
                        }
                        if (DownloadUtils.isLastChunk(headers)) {
                            resourceState = Enums.VariableState.ok;
                            break;
                        }
                        if (partFile.length()==0) {
                            resourceState = Enums.VariableState.ok;
                            break;
                        }
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == HttpServletResponse.SC_GONE) {
                            resourceState = setVariableWasntFound(task);
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_BAD_GATEWAY ) {
                            es = String.format("#810.035 BAD_GATEWAY error while downloading a variable #%s. will try later again", task.variableId);
                            log.warn(es);
                            // do nothing and try later again
                            return;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE ) {
                            es = String.format("#810.036 Unknown error with a resource %s. Task #%s is finished.", task.variableId, task.getTaskId());
                            log.warn(es);
                            processorTaskService.markAsFinishedWithError(task.ref, task.getTaskId(), es);
                            resourceState = Enums.VariableState.unknown_error;
                            break;
                        }
                        else if (e.getStatusCode() == HttpServletResponse.SC_NOT_ACCEPTABLE) {
                            es = String.format("#810.037 Unknown error with a resource %s. Task #%s is finished.", task.variableId, task.getTaskId());
                            log.warn(es);
                            processorTaskService.markAsFinishedWithError(task.ref, task.getTaskId(), es);
                            resourceState = Enums.VariableState.unknown_error;
                            break;
                        }
                        else {
                            es = String.format("#810.037 An unknown error while downloading a variable #%s. Task #%s is finished with an error.", task.variableId, task.getTaskId());
                            log.warn(es);
                            processorTaskService.markAsFinishedWithError(task.ref, task.getTaskId(), es);
                            resourceState = Enums.VariableState.unknown_error;
                            break;
                        }
                    }
                    catch(SocketTimeoutException e) {
                        log.error("#810.040 SocketTimeoutException, uri: " + uri+", " + e.getMessage());
                        return;
                    }
                    catch(ConnectException e) {
                        log.error("#810.042 ConnectException, uri: " + uri+", " + e.getMessage());
                        return;
                    }
                    idx++;
                } while (idx<1000);
                if (resourceState == Enums.VariableState.none) {
                    log.error("#810.050 something wrong, is file too big or chunkSize too small? chunkSize: {}", dispatcherContextInfo.chunkSize);
                    continue;
                }
                else if (resourceState == Enums.VariableState.unknown_error || resourceState == Enums.VariableState.variable_doesnt_exist) {
                    log.warn("#810.053 Variable {} can't be acquired, state: {}", task.variableId, resourceState);
                    continue;
                }
                else if (resourceState == Enums.VariableState.transmitting_error || resourceState == Enums.VariableState.variable_cant_be_null) {
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
                log.info("Variable #{} was loaded", task.variableId);
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpServletResponse.SC_CONFLICT) {
                    log.warn("#810.080 Variable with id {} is broken and need to be recreated", task.variableId);
                } else {
                    log.error("#810.090 HttpResponseException.getStatusCode(): {}", e.getStatusCode());
                    log.error("#810.091 HttpResponseException", e);
                }
            } catch (SocketTimeoutException e) {
                log.error("#810.100 SocketTimeoutException, error: {}", e.getMessage());
            } catch (IOException e) {
                log.error("#810.110 IOException", e);
            } catch (URISyntaxException e) {
                log.error("#810.120 URISyntaxException, error: {} ", e.getMessage());
            }
        }
    }

    private Enums.VariableState setVariableWasntFound(DownloadVariableTask task) {
        String es;
        es = String.format("#810.027 Variable %s wasn't found on dispatcher. Set state of task #%s to 'finished' with error.", task.variableId, task.getTaskId());
        log.warn(es);
        processorTaskService.markAsFinishedWithError(task.ref, task.getTaskId(), es);
        return Enums.VariableState.variable_doesnt_exist;
    }
}