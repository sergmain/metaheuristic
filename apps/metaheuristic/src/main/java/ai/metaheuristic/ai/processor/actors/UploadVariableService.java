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
package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.processor.CurrentExecState;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.HttpUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class UploadVariableService extends AbstractTaskQueue<UploadVariableTask> implements QueueProcessor {

    private static final ObjectMapper mapper;
    private final CurrentExecState currentExecState;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private static final Random R = new Random();

    private static UploadResult fromJson(String json) {
        try {
            UploadResult result = mapper.readValue(json, UploadResult.class);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("311.010 error", e);
        }
    }

    // key - variableId, value -stub value
    private final HashMap<Long, Boolean> variableIds = new HashMap<>();
    private static final ReentrantLock LOCK = new ReentrantLock();

    @SneakyThrows
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        UploadVariableTask task1;
        List<UploadVariableTask> repeat = new ArrayList<>();
        while((task1 = poll())!=null) {
            LOCK.lock();
            try {
                if (variableIds.containsKey(task1.variableId)) {
                    continue;
                }
                variableIds.put(task1.variableId, Boolean.TRUE);
            }
            finally {
                LOCK.unlock();
            }
            final UploadVariableTask task = task1;
            Thread.ofVirtual().start(()-> {
                try {
                    final UploadVariableTask finalTask = task;

                    ProcessorCoreTask processorTask = processorTaskService.findByIdForCore(task.core, task.taskId);
                    if (processorTask == null) {
                        log.info("311.020 task was already cleaned or didn't exist, {}, #{}", task.getDispatcherUrl(), task.taskId);
                        return;
                    }
                    if (currentExecState.finishedOrDoesntExist(task.core.dispatcherUrl, processorTask.execContextId)) {
                        log.info("311.021 ExecContext #{} for task #{} with variable #{} is finished or doesn't exist, url: {}",
                            processorTask.execContextId, task.taskId, finalTask.variableId, task.getDispatcherUrl());
                        processorTaskService.setVariableUploadedAndCompleted(task.core, finalTask.taskId, finalTask.variableId);
                        return;
                    }
                    final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.UTILS.to(processorTask.getParams());

                    TaskParamsYaml.OutputVariable v = taskParamYaml.task.outputs.stream().filter(o -> o.id.equals(finalTask.variableId)).findFirst().orElse(null);
                    if (v == null) {
                        log.error("311.022 outputVariable with variableId {} wasn't found.", finalTask.variableId);
                        processorTaskService.delete(task.core, task.taskId);
                        return;
                    }
                    ProcessorCoreTask.OutputStatus outputStatus = processorTask.output.outputStatuses.stream().filter(o -> o.variableId.equals(finalTask.variableId)).findFirst().orElse(null);
                    if (outputStatus == null) {
                        log.error("311.024 outputStatus for variableId {} wasn't found.", finalTask.variableId);
                        processorTaskService.delete(task.core, task.taskId);
                        return;
                    }
                    if (outputStatus.uploaded) {
                        log.info("311.030 resource was already uploaded, {}, #{}", task.getDispatcherUrl(), task.taskId);
                        return;
                    }
                    if (v.sourcing != EnumsApi.DataSourcing.dispatcher) {
                        throw new NotImplementedException("311.032 Need to implement");
                    }
                    if (!task.nullified && (task.file == null || Files.notExists(task.file))) {
                        log.error("311.040 File {} doesn't exist", task.file.toAbsolutePath());
                        return;
                    }
                    if (task.nullified) {
                        log.info("Start reporting a variable #{} as null to {}", task.variableId, task.getDispatcherUrl().url);
                    } else {
                        log.info("Start uploading a variable #{} to server {}, resultDataFile: {}", task.variableId, task.getDispatcherUrl().url, task.file);
                    }
                    Enums.UploadVariableStatus status = null;
                    try {
                        final Executor executor = HttpClientExecutor.getExecutor(task.getDispatcherUrl().url, task.dispatcher.restUsername, task.dispatcher.restPassword);

                        final Boolean readyForUploading = isVariableReadyForUploading(task.getDispatcherUrl().url, task.variableId, executor);
                        if (readyForUploading == null) {
                            log.info("variable #{} in task #{} doesn't exist at dispatcher", task.variableId, task.taskId);
                            processorTaskService.setVariableUploadedAndCompleted(task.core, task.taskId, finalTask.variableId);
                            return;
                        }
                        if (!readyForUploading) {
                            log.info("variable #{} in task #{} was already inited", task.variableId, task.taskId);
                            processorTaskService.setVariableUploadedAndCompleted(task.core, task.taskId, finalTask.variableId);
                            return;
                        }

                        final String uploadRestUrl = task.getDispatcherUrl().url + CommonConsts.REST_V1_URL + Consts.UPLOAD_REST_URL;
                        String randonPart = "/" + R.nextInt(100_000, 1_000_000) + '-' + task.core.processorId + '-' + task.taskId;
                        final String uri = uploadRestUrl + randonPart;

                        final MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                            .setMode(HttpMultipartMode.EXTENDED)
                            .setCharset(StandardCharsets.UTF_8)
                            .addTextBody("processorId", "" + task.core.processorId)
                            .addTextBody("taskId", Long.toString(task.taskId))
                            .addTextBody("variableId", Long.toString(task.variableId))
                            .addTextBody("nullified", String.valueOf(task.nullified));

                        if (!task.nullified) {
                            if (task.file == null) {
                                // TODO 2020-11-26 in case that ai.metaheuristic.ai.processor.tasks.UploadVariableTask.file is @Nullable
                                //  what is the problem with this state? Should we handle this state in more sophisticated way?
                                throw new IllegalStateException("311.043 (task.file==null)");
                            }
                            log.info("variable #{} has length {}", task.variableId, Files.size(task.file));
                            builder.addBinaryBody("file", task.file.toFile(), ContentType.APPLICATION_OCTET_STREAM, task.file.getFileName().toString());
                        }
                        HttpEntity entity = builder.build();

                        Request request = Request.post(uri)
                            .connectTimeout(Timeout.ofSeconds(5))
                            .responseTimeout(Timeout.ofSeconds(60))
//                        .socketTimeout(20000)
                            .body(entity);

                        log.info("Start uploading a variable to rest-server, {}", randonPart);
                        Response response;
                        long mills = System.currentTimeMillis();
                        try {
                            response = executor.execute(request);
                        } finally {
                            log.info("executor.execute() took {} mills", System.currentTimeMillis() - mills);
                        }
                        String json = response.returnContent().asString(StandardCharsets.UTF_8);
                        UploadResult result = fromJson(json);
                        log.info("Server response: {}", result);

                        if (result.status != Enums.UploadVariableStatus.OK) {
                            log.error("311.050 Error uploading file, server's error : " + result.error);
                        }
                        status = result.status;

                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == 401) {
                            log.error("311.055 Error uploading variable to server, code: 401, error: {}", e.getMessage());
                        } else if (e.getStatusCode() == 500) {
                            log.error("311.056 Server error, code: 500, error: {}", e.getMessage());
                        } else {
                            String s;
                            if (task.file != null) {
                                try {
                                    s = Long.toString(Files.size(task.file));
                                } catch (IOException ex) {
                                    s = "IOException, size is unknown";
                                }
                            }
                            else {
                                s = "file is null";
                            }
                            log.error("311.060 Error uploading variable to server, code: " + e.getStatusCode() + ", " +
                                "size: " + s, e);
                        }
                    } catch (ConnectException e) {
                        log.error("311.073 ConnectException, {}", e.toString());
                    } catch (NoHttpResponseException e) {
                        log.error("311.075 org.apache.http.NoHttpResponseException, {}", e.toString());
                    } catch (ConnectTimeoutException e) {
                        log.warn("311.076 org.apache.http.conn.ConnectTimeoutException, {}", e.toString());
                    } catch (SocketTimeoutException e) {
                        log.error("311.070 SocketTimeoutException, {}", e.toString());
                    } catch (java.net.UnknownHostException e) {
                        log.warn("311.077 java.net.UnknownHostException, {}", e.toString());
                    }
                    catch (IOException e) {
                        log.error("311.080 IOException", e);
                    } catch (Throwable th) {
                        log.error("311.090 Throwable", th);
                    }
                    if (status != null) {
                        switch (status) {
                            case VARIABLE_NOT_FOUND:
                                // right now we will assume that it's ok to set as UploadedAndCompleted
                            case OK:
                                log.info("Variable #{} was successfully uploaded to server, {}, {} ", finalTask.variableId, task.getDispatcherUrl(), task.taskId);
                                processorTaskService.setVariableUploadedAndCompleted(task.core, task.taskId, finalTask.variableId);
                                break;
                            case FILENAME_IS_BLANK:
                            case TASK_WAS_RESET:
                            case TASK_NOT_FOUND:
                            case UNRECOVERABLE_ERROR:
                                processorTaskService.delete(task.core, task.taskId);
                                log.error("311.100 server return status {}, this task will be deleted.", status);
                                break;
                            case PROBLEM_WITH_LOCKING:
                                log.warn("311.110 problem with locking in DB at server side, {}", status);
                                repeat.add(task);
                                break;
                            case GENERAL_ERROR:
                                log.warn("311.120 general error at server side, {}", status);
                                repeat.add(task);
                                break;
                        }
                    } else {
                        log.warn("311.130 Error accessing rest-server. Assign task one more time.");
                        repeat.add(task);
                    }
                } finally {
                    LOCK.lock();
                    try {
                        variableIds.remove(task.variableId);
                    }
                    finally {
                        LOCK.unlock();
                    }
                    for (UploadVariableTask uploadResourceTask : repeat) {
                        add(uploadResourceTask);
                    }
                }
            });
        }
    }

    @Nullable
    private static Boolean isVariableReadyForUploading(String dispatcherUrl, Long variableId, Executor executor) {
        final String variableStatusRestUrl = dispatcherUrl + CommonConsts.REST_V1_URL + Consts.VARIABLE_STATUS_REST_URL;

        try {
            final URI build = new URIBuilder(variableStatusRestUrl).setCharset(StandardCharsets.UTF_8).build();
            final Request request = Request.post(build)
                    .bodyForm(Form.form().add("variableId", variableId.toString()).build(), StandardCharsets.UTF_8)
                    .connectTimeout(Timeout.ofSeconds(5));
                    //.socketTimeout(20000);

            RestUtils.addHeaders(request);
            Response response = executor.execute(request);

            final HttpResponse httpResponse = response.returnResponse();
            if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
                throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
            }
            final int statusCode = classicHttpResponse.getCode();
            if (statusCode!=200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final HttpEntity entity = classicHttpResponse.getEntity();
                if (entity != null) {
                    entity.writeTo(baos);
                }

                log.error("Server response:\n{}", baos.toString());
                // let's try to upload variable anyway
                return true;
            }
            final HttpEntity entity = classicHttpResponse.getEntity();
            if (entity != null) {
                String value;
                try (final InputStream content = entity.getContent()) {
                    value = IOUtils.toString(content, StandardCharsets.UTF_8);
                }
                // right now uri /variable-status returns value of 'inited' field

                return switch (value) {
                    case "false" -> true;
                    case "true" -> false;
                    default -> null;
                };
            }
            else {
                return true;
            }        }
        catch (UnknownHostException | HttpHostConnectException | SocketTimeoutException th) {
            log.error("Error: {}", th.getMessage());
            return true;
        }
        catch (Throwable th) {
            log.error("Error", th);
            return true;
        }
    }

    private static Executor getExecutor(String dispatcherUrl, String restUsername, String restPassword) {
        HttpHost dispatcherHttpHostWithAuth;
        try {
            dispatcherHttpHostWithAuth = HttpUtils.getHttpHost(dispatcherUrl);
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for " + dispatcherUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(dispatcherHttpHostWithAuth)
                .auth(dispatcherHttpHostWithAuth, restUsername, restPassword.toCharArray());
    }

}
