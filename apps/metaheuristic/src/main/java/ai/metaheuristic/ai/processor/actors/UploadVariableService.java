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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class UploadVariableService extends AbstractTaskQueue<UploadVariableTask> implements QueueProcessor {

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;

    private static UploadResult fromJson(String json) {
        try {
            UploadResult result = mapper.readValue(json, UploadResult.class);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("#311.010 error", e);
        }
    }

    @SuppressWarnings("Duplicates")
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        UploadVariableTask task;
        List<UploadVariableTask> repeat = new ArrayList<>();
        while((task = poll())!=null) {
            ProcessorTask processorTask = processorTaskService.findById(task.ref, task.taskId);
            if (processorTask == null) {
                log.info("#311.020 task was already cleaned or didn't exist, {}, #{}", task.getDispatcherUrl(), task.taskId);
                continue;
            }
            final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(processorTask.getParams());
            final UploadVariableTask finalTask = task;

            TaskParamsYaml.OutputVariable v = taskParamYaml.task.outputs.stream().filter(o->o.id.equals(finalTask.variableId)).findFirst().orElse(null);
            if (v==null) {
                log.error("#311.022 outputVariable with variableId {} wasn't found.", finalTask.variableId);
                processorTaskService.delete(task.ref, task.taskId);
                continue;
            }
            ProcessorTask.OutputStatus outputStatus = processorTask.output.outputStatuses.stream().filter(o->o.variableId.equals(finalTask.variableId)).findFirst().orElse(null);
            if (outputStatus==null) {
                log.error("#311.024 outputStatus for variableId {} wasn't found.", finalTask.variableId);
                processorTaskService.delete(task.ref, task.taskId);
                continue;
            }
            if (outputStatus.uploaded) {
                log.info("#311.030 resource was already uploaded, {}, #{}", task.getDispatcherUrl(), task.taskId);
                continue;
            }
            if (v.sourcing!= EnumsApi.DataSourcing.dispatcher) {
                throw new NotImplementedException("#311.032 Need to implement");
            }
            if (!task.nullified && (task.file==null || !task.file.exists())) {
                log.error("#311.040 File {} doesn't exist", task.file.getPath());
                continue;
            }
            if (task.nullified) {
                log.info("Start reporting a variable #{} as null", task.variableId);
            }
            else {
                log.info("Start uploading a variable #{} to server, resultDataFile: {}", task.variableId, task.file);
            }
            Enums.UploadVariableStatus status = null;
            try {
                final Executor executor = HttpClientExecutor.getExecutor(task.getDispatcherUrl().url, task.dispatcher.restUsername, task.dispatcher.restPassword);

                if (!isVariableReadyForUploading(task.getDispatcherUrl().url, task.variableId, executor)) {
                    continue;
                }

                final String uploadRestUrl  = task.getDispatcherUrl().url + CommonConsts.REST_V1_URL + Consts.UPLOAD_REST_URL;
                String randonPart = '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + task.ref.processorId + '-' + task.taskId;
                final String uri = uploadRestUrl + randonPart;

                final MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.RFC6532)
                        .setCharset(StandardCharsets.UTF_8)
                        .addTextBody("processorId", task.ref.processorId)
                        .addTextBody("taskId", Long.toString(task.taskId))
                        .addTextBody("variableId", Long.toString(task.variableId))
                        .addTextBody("nullified", String.valueOf(task.nullified));

                if (!task.nullified) {
                    if (task.file==null) {
                        // TODO 2020-11-26 in case that ai.metaheuristic.ai.processor.tasks.UploadVariableTask.file is @Nullable
                        //  what is the problem with this state? Should we handle this state in more sophisticated way?
                        throw new IllegalStateException("#311.043 (task.file==null)");
                    }
                    builder.addBinaryBody("file", task.file, ContentType.APPLICATION_OCTET_STREAM, task.file.getName());
                }
                HttpEntity entity = builder.build();

                Request request = Request.Post(uri).connectTimeout(5000).socketTimeout(20000).body(entity);

                log.info("Start uploading a variable to rest-server, {}", randonPart);
                Response response = executor.execute(request);
                String json = response.returnContent().asString();
                UploadResult result = fromJson(json);
                log.info("Server response: {}", result);

                if (result.status!= Enums.UploadVariableStatus.OK) {
                    log.error("#311.050 Error uploading file, server's error : " + result.error);
                }
                status = result.status;

            } catch (HttpResponseException e) {
                if (e.getStatusCode()==401) {
                    log.error("#311.055 Error uploading resource to server, code: 401, error: {}", e.getMessage());
                }
                else if (e.getStatusCode()== 500) {
                    log.error("#311.056 Server error, code: 500, error: {}", e.getMessage());
                }
                else {
                    log.error("#311.060 Error uploading resource to server, code: " + e.getStatusCode(), e);
                }
            }
            catch (SocketTimeoutException e) {
                log.error("#311.070 SocketTimeoutException, {}", e.toString());
            }
            catch (ConnectException e) {
                log.error("#311.073 ConnectException, {}", e.toString());
            }
            catch (NoHttpResponseException e) {
                log.error("#311.075 org.apache.http.NoHttpResponseException, {}", e.toString());
            }
            catch (org.apache.http.conn.ConnectTimeoutException e) {
                log.warn("#311.076 org.apache.http.conn.ConnectTimeoutException, {}", e.toString());
            }
            catch (java.net.UnknownHostException e) {
                log.warn("#311.077 java.net.UnknownHostException, {}", e.toString());
            }
            catch (IOException e) {
                log.error("#311.080 IOException", e);
            }
            catch (Throwable th) {
                log.error("#311.090 Throwable", th);
            }
            if (status!=null) {
                switch(status) {
                    case VARIABLE_NOT_FOUND:
                        // right we will assume that it's ok to set as UploadedAndCompleted
                    case OK:
                        log.info("Variable #{} was successfully uploaded to server, {}, {} ", finalTask.variableId, task.getDispatcherUrl(), task.taskId);
                        processorTaskService.setVariableUploadedAndCompleted(task.ref, task.taskId, finalTask.variableId);
                        break;
                    case FILENAME_IS_BLANK:
                    case TASK_WAS_RESET:
                    case TASK_NOT_FOUND:
                    case UNRECOVERABLE_ERROR:
                        processorTaskService.delete(task.ref, task.taskId);
                        log.error("#311.100 server return status {}, this task will be deleted.", status);
                        break;
                    case PROBLEM_WITH_LOCKING:
                        log.warn("#311.110 problem with locking in DB at server side, {}", status);
                        repeat.add(task);
                        break;
                    case GENERAL_ERROR:
                        log.warn("#311.120 general error at server side, {}", status);
                        repeat.add(task);
                        break;
                }
            }
            else {
                log.warn("#311.130 Error accessing rest-server. Assign task one more time.");
                repeat.add(task);
            }
        }
        for (UploadVariableTask uploadResourceTask : repeat) {
            add(uploadResourceTask);
        }
    }

    private static boolean isVariableReadyForUploading(String dispatcherUrl, Long variableId, Executor executor) {
        final String variableStatusRestUrl = dispatcherUrl + CommonConsts.REST_V1_URL + Consts.VARIABLE_STATUS_REST_URL;

        try {
            final URI build = new URIBuilder(variableStatusRestUrl).setCharset(StandardCharsets.UTF_8).build();
            final Request request = Request.Post(build)
                    .bodyForm(Form.form().add("variableId", ""+variableId).build(), StandardCharsets.UTF_8)
                    .connectTimeout(5000).socketTimeout(20000);

            RestUtils.addHeaders(request);
            Response response = executor.execute(request);

            final HttpResponse httpResponse = response.returnResponse();
            if (httpResponse.getStatusLine().getStatusCode()!=200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    entity.writeTo(baos);
                }

                log.error("Server response:\n" + baos.toString());
                // let's try to upload variable anyway
                return true;
            }
            final HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                String value = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
                // right now uri /variable-status returns value of 'inited' field

                return "false".equals(value);
            }
            else {
                return true;
            }
        }
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
            dispatcherHttpHostWithAuth = URIUtils.extractHost(new URL(dispatcherUrl).toURI());
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for " + dispatcherUrl, th);
        }
        return Executor.newInstance()
                .authPreemptive(dispatcherHttpHostWithAuth)
                .auth(dispatcherHttpHostWithAuth, restUsername, restPassword);
    }

}