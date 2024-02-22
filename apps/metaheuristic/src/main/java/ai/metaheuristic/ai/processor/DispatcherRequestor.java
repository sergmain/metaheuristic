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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.event.RequestDispatcherForNewTaskEvent;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.processor.ws.ProcessorWebsocketService;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParams;
import ai.metaheuristic.ai.yaml.ws_event.WebsocketEventParamsUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.exceptions.CustomInterruptedException;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.client.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static ai.metaheuristic.commons.CommonConsts.*;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

@SuppressWarnings("FieldCanBeLocal")
@Slf4j
public class DispatcherRequestor {

    private final DispatcherUrl dispatcherUrl;
    private final Globals globals;

    private final ProcessorTaskService processorTaskService;
    private final ProcessorService processorService;
    private final MetadataParams metadataService;
    private final CurrentExecState currentExecState;
    private final ProcessorCommandProcessor processorCommandProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();

    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher;
    private final String dispatcherRestUrl;
    private final String dispatcherWsUrl;
    @Nullable
    private final ProcessorWebsocketService.WebSocketInfra wsInfra;
    private static final Random R = new Random();
    private Enums.RequestToDispatcherType defaultTaskRequest = Enums.RequestToDispatcherType.both;

    private final MultiTenantedQueue<Enums.WebsocketEventType, RequestDispatcherForNewTaskEvent> MULTI_TENANTED_QUEUE =
        new MultiTenantedQueue<>(2, ConstsApi.SECONDS_1, true, null, this::handleRequestDispatcherForNewTaskEvent);

    public DispatcherRequestor(
        DispatcherUrl dispatcherUrl, Globals globals, ProcessorTaskService processorTaskService,
        ProcessorService processorService, MetadataParams metadataService, CurrentExecState currentExecState,
        DispatcherLookupExtendedParams dispatcherLookupExtendedService, ProcessorCommandProcessor processorCommandProcessor,
        boolean websocketEnabled) {

        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.processorService = processorService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.processorCommandProcessor = processorCommandProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        // in Spring Boot 2.2.4 it should be working without this call
        // in some cases it isn't working even with 2.2.4 without this call
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (dispatcher == null) {
            throw new IllegalStateException("775.030 Can't find dispatcher config for url " + dispatcherUrl);
        }
        dispatcherRestUrl = dispatcherUrl.url + REST_V1_URL + Consts.SERVER_REST_URL_V2;
        dispatcherWsUrl = getDispatcherWsUrl(dispatcherUrl);

        if (websocketEnabled && !dispatcher.dispatcherLookup.disabled) {
            wsInfra = new ProcessorWebsocketService.WebSocketInfra(
                dispatcherWsUrl,
                dispatcher.dispatcherLookup.getRestUsername(),
                dispatcher.dispatcherLookup.getRestPassword(),
                this::consumeDispatcherEvent);
            wsInfra.runInfra();
        }
        else {
            wsInfra = null;
        }
    }

    @NonNull
    private static String getDispatcherWsUrl(DispatcherUrl dispatcherUrl) {
        final String url = dispatcherUrl.url + Consts.WS_DISPATCHER_URL;
        String wsUrl;
        if (url.startsWith(HTTP)) {
            wsUrl = url.substring(HTTP.length());
        }
        else if (url.startsWith(HTTPS)) {
            wsUrl = url.substring(HTTPS.length());
        }
        else {
            throw new IllegalStateException("Unknown protocol in url: " + url);
        }
        return WS_PROTOCOL + wsUrl;
    }

    public void destroy() {
        if (wsInfra!=null) {
            wsInfra.destroy();
        }
        MULTI_TENANTED_QUEUE.shutdown();
    }

    private long lastRequestForMissingResources = 0;
    private long lastCheckForResendTaskOutputResource = 0;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private void withSync(Runnable function) {
        writeLock.lock();
        try {
            function.run();
        } finally {
            writeLock.unlock();
        }
    }

    private void consumeDispatcherEvent(String event) {
        WebsocketEventParams params = WebsocketEventParamsUtils.BASE_UTILS.to(event);
        MULTI_TENANTED_QUEUE.putToQueue(new RequestDispatcherForNewTaskEvent(params));
    }

    private void handleRequestDispatcherForNewTaskEvent(RequestDispatcherForNewTaskEvent event) {
        log.info("77.060 new event "+event.params.type+" from dispatcher via WS, " + dispatcherWsUrl);
        if (event.params.type== Enums.WebsocketEventType.task) {
            requestNewTaskImmediately();
        }
        if (event.params.type== Enums.WebsocketEventType.function) {
            throw new IllegalStateException("77.090 Not implemented yet");
        }
    }

    private void requestNewTaskImmediately() {
        // if we received request via websocket, then downgrade default request type to main
        defaultTaskRequest = Enums.RequestToDispatcherType.main;
        proceedWithRequest(Enums.RequestToDispatcherType.task);
    }

    private void processDispatcherCommParamsYaml(ProcessorCommParamsYaml scpy, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml dispatcherYaml) {
        log.debug("775.120 DispatcherCommParamsYaml:\n{}", dispatcherYaml);
        withSync(() -> {
            processorCommandProcessor.processDispatcherCommParamsYaml(scpy, dispatcherUrl, dispatcherYaml);
        });
    }

    public void proceedWithRequest() {
        proceedWithRequest(defaultTaskRequest);
    }

    private void proceedWithRequest(Enums.RequestToDispatcherType taskRequest) {
        if (globals.testing || !globals.processor.enabled) {
            return;
        }
        if (dispatcher.dispatcherLookup.disabled) {
            log.warn("775.150 dispatcher {} is disabled", dispatcherUrl.url);
            return;
        }

        try {
            final ProcessorCommParamsYaml pcpy = prepareProcessorCommParamsYaml(taskRequest);
            if (!newRequest(pcpy)) {
                log.info("775.180 no new requests to {}", dispatcherUrl.url );
                return;
            }

            final String url = dispatcherRestUrl + '/' + R.nextInt(100_000, 1_000_000);
            String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(pcpy);

            log.info("Start to request a dispatcher at {}, quotas: {}", url, pcpy.request.currentQuota);

            final String result = RestUtils.makeRequest(restTemplate, url, yaml, dispatcher.authHeader, dispatcherRestUrl);

            if (result == null) {
                log.warn("775.210 Dispatcher returned null as a result");
                return;
            }
            DispatcherCommParamsYaml dispatcherYaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(result);

            if (!dispatcherYaml.success) {
                log.error("775.240 Something wrong at the dispatcher {}. Check the dispatcher's logs for more info.", dispatcherUrl );
                return;
            }
            processDispatcherCommParamsYaml(pcpy, dispatcherUrl, dispatcherYaml);
        }
        catch (CustomInterruptedException | InterruptedException e) {
            //
        }
        catch (Throwable e) {
            log.error("775.270 Error in fixedDelay(), url: {}, error: {}", dispatcherRestUrl, e.getMessage());
        }
    }

    private ProcessorCommParamsYaml prepareProcessorCommParamsYaml(Enums.RequestToDispatcherType taskRequest) {
        ProcessorCommParamsYaml pcpy = new ProcessorCommParamsYaml();

        ProcessorCommParamsYaml.ProcessorRequest r = pcpy.request;

        MetadataParamsYaml.ProcessorSession ps = metadataService.getProcessorSession(dispatcherUrl);
        final Long processorId = ps.processorId;
        final String sessionId = ps.sessionId;

        if (processorId == null || sessionId == null) {
            if (taskRequest.isMain) {
                r.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId();
            }
            else {
                // return empty request in case when e need a new task but session wasn't initialized yet
                return pcpy;
            }
        }
        else {
            r.currentQuota = metadataService.currentQuota(dispatcherUrl.url);
            r.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorId, sessionId);

            if (System.currentTimeMillis() - lastRequestForMissingResources > 30_000) {
                r.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources();
                lastRequestForMissingResources = System.currentTimeMillis();
            }
            for (Map.Entry<String, Long> entry : ps.cores.entrySet()) {
                String coreCode = entry.getKey();
                final Long coreId = entry.getValue();
                ProcessorCommParamsYaml.Core coreParams = new ProcessorCommParamsYaml.Core(coreCode, coreId, null);
                r.cores.add(coreParams);

                ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core =
                        new ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef(dispatcherUrl, ps.dispatcherCode, processorId, coreCode, coreId);

                if (taskRequest.isTask) {
                    if (currentExecState.getCurrentInitState(dispatcherUrl)== Enums.ExecContextInitState.FULL) {
                        final boolean b = processorTaskService.isNeedNewTask(core);
                        if (b && dispatcher.schedule.isCurrentTimeActive()) {
                            // don't report about tasks which belong to finished execContext
                            final String taskIds = processorTaskService.findAllForCore(core).stream()
                                .filter(o -> currentExecState.notFinishedAndExists(dispatcher.dispatcherUrl, o.execContextId))
                                .map(o -> o.taskId.toString()).collect(Collectors.joining(","));
                            coreParams.requestTask = new ProcessorCommParamsYaml.RequestTask(dispatcher.dispatcherLookup.signatureRequired, taskIds);
                        }
                    }
                }

                if (taskRequest.isMain) {
                    // we have to pull new tasks from server constantly only if a list of execContextIds was initialized fully
                    if (currentExecState.getCurrentInitState(dispatcherUrl)== Enums.ExecContextInitState.FULL) {
                        if (System.currentTimeMillis() - lastCheckForResendTaskOutputResource > 30_000) {
                            // let's check variables for not completed and not sent yet tasks
                            List<ProcessorCoreTask> processorTasks = processorTaskService.findAllByCompletedIsFalse(core).stream()
                                .filter(t -> t.delivered && t.finishedOn != null && !t.output.allUploaded())
                                .filter(t -> currentExecState.notFinishedAndExists(core.dispatcherUrl, t.execContextId))
                                .collect(Collectors.toList());

                            List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
                            for (ProcessorCoreTask processorTask : processorTasks) {
                                for (ProcessorCoreTask.OutputStatus outputStatus : processorTask.output.outputStatuses) {
                                    statuses.add(new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(
                                            processorTask.taskId, outputStatus.variableId,
                                            processorService.resendTaskOutputResources(core, processorTask.taskId, outputStatus.variableId))
                                    );
                                }
                            }
                            pcpy.addResendTaskOutputResourceResult(statuses);
                            lastCheckForResendTaskOutputResource = System.currentTimeMillis();
                        }
                    }
                    pcpy.addReportTaskProcessingResult(processorTaskService.reportTaskProcessingResult(core));
                }
            }
        }
        return pcpy;
    }

    private static boolean newRequest(ProcessorCommParamsYaml pcpy) {
        ProcessorCommParamsYaml.ProcessorRequest request = pcpy.request;
        boolean state = request.requestProcessorId!=null ||
                request.checkForMissingOutputResources!=null ||
                request.resendTaskOutputResourceResult!=null ||
                request.reportTaskProcessingResult!=null ||
                request.cores.stream().anyMatch(core -> Objects.nonNull(core.requestTask));

        return state;
    }

}


