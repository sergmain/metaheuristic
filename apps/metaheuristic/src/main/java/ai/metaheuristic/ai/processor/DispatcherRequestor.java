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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.processor.processor_environment.MetadataParams;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.commons.CommonConsts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.*;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static org.springframework.http.HttpStatus.*;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

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
    private final String serverRestUrl;

    public DispatcherRequestor(DispatcherUrl dispatcherUrl, Globals globals, ProcessorTaskService processorTaskService, ProcessorService processorService, MetadataParams metadataService, CurrentExecState currentExecState, DispatcherLookupExtendedParams dispatcherLookupExtendedService, ProcessorCommandProcessor processorCommandProcessor) {
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
            throw new IllegalStateException("#775.010 Can't find dispatcher config for url " + dispatcherUrl);
        }
        serverRestUrl = dispatcherUrl.url + CommonConsts.REST_V1_URL + Consts.SERVER_REST_URL_V2;
    }

    private long lastRequestForMissingResources = 0;
    private long lastCheckForResendTaskOutputResource = 0;

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private static void withSync(Supplier<Void> function) {
        writeLock.lock();
        try {
            function.get();
        } finally {
            writeLock.unlock();
        }
    }

    private void processDispatcherCommParamsYaml(ProcessorCommParamsYaml scpy, DispatcherUrl dispatcherUrl, DispatcherCommParamsYaml dispatcherYaml) {
        log.debug("#775.020 DispatcherCommParamsYaml:\n{}", dispatcherYaml);
        withSync(() -> {
            processorCommandProcessor.processDispatcherCommParamsYaml(scpy, dispatcherUrl, dispatcherYaml);
            return null;
        });
    }

    public void proceedWithRequest() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }
        if (dispatcher.dispatcherLookup.disabled) {
            log.warn("#775.020 dispatcher {} is disabled", dispatcherUrl.url);
            return;
        }

        ProcessorCommParamsYaml pcpy = new ProcessorCommParamsYaml();

        try {
            ProcessorCommParamsYaml.ProcessorRequest r = pcpy.request;
            r.currentQuota = metadataService.currentQuota(dispatcherUrl.url);

            MetadataParamsYaml.ProcessorSession ps = metadataService.getProcessorSession(dispatcherUrl);
            final Long processorId = ps.processorId;
            final String sessionId = ps.sessionId;

            if (processorId == null || sessionId == null) {
                r.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId();
            }
            else {
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

                    // we have to pull new tasks from server constantly only if a list of execContextIds was initialized fully
                    if (currentExecState.getCurrentInitState(dispatcherUrl)== Enums.ExecContextInitState.FULL) {
                        final boolean b = processorTaskService.isNeedNewTask(core);
                        if (b && dispatcher.schedule.isCurrentTimeActive()) {
                            // always report about current active tasks, if we have actual processorId
                            // don't report about tasks which belong to finished execContext
                            final String taskIds = processorTaskService.findAllForCore(core).stream()
                                    .filter(o -> currentExecState.notFinishedAndExists(dispatcher.dispatcherUrl, o.execContextId))
                                    .map(o -> o.taskId.toString()).collect(Collectors.joining(","));
                            coreParams.requestTask = new ProcessorCommParamsYaml.RequestTask(dispatcher.dispatcherLookup.signatureRequired, taskIds);
                        }
                        else {
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
                    }
                    pcpy.addReportTaskProcessingResult(processorTaskService.reportTaskProcessingResult(core));
                }
            }

            if (!newRequest(pcpy)) {
                log.info("#775.045 no new requests to {}", dispatcherUrl.url );
                return;
            }

            final String url = serverRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8);
            try {
                // TODO 2021-02-18 refactor as a common method
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.setContentType(MediaType.APPLICATION_JSON);

                String auth = dispatcher.dispatcherLookup.restUsername + ':' + dispatcher.dispatcherLookup.restPassword;
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);

                String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(pcpy);
                HttpEntity<String> request = new HttpEntity<>(yaml, headers);

                log.debug("Start to request a dispatcher at {}", url);
                log.debug("ExchangeData:\n{}", yaml);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                String result = response.getBody();
                log.debug("ExchangeData from dispatcher:\n{}", result);
                if (result == null) {
                    log.warn("#775.050 Dispatcher returned null as a result");
                    return;
                }
                DispatcherCommParamsYaml dispatcherYaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(result);

                if (!dispatcherYaml.success) {
                    log.error("#775.060 Something wrong at the dispatcher {}. Check the dispatcher's logs for more info.", dispatcherUrl );
                    return;
                }
                processDispatcherCommParamsYaml(pcpy, dispatcherUrl, dispatcherYaml);

            } catch (HttpClientErrorException e) {
                int value = e.getStatusCode().value();
                if (value==UNAUTHORIZED.value() || value==FORBIDDEN.value() || value==NOT_FOUND.value()) {
                    log.error("#775.070 Error {} accessing url {}", e.getStatusCode().value(), serverRestUrl);
                }
                else {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SocketException) {
                    log.error("#775.090 Connection error: url: {}, err: {}", url, cause.getMessage());
                }
                else if (cause instanceof UnknownHostException) {
                    log.error("#775.093 Host unreachable, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof ConnectTimeoutException) {
                    log.warn("#775.095 Connection timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof SocketTimeoutException) {
                    log.warn("#775.097 Socket timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof SSLPeerUnverifiedException) {
                    log.error("#775.098 SSL certificate mismatched, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof SSLException) {
                    log.error("#775.098 SSL error, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else {
                    log.error("#775.100 Error, url: " + url, e);
                }
            } catch (RestClientException e) {
                if (e instanceof HttpStatusCodeException httpStatusCodeException && httpStatusCodeException.getStatusCode().value()>=500 && httpStatusCodeException.getStatusCode().value()<600 ) {
                    int errorCode = httpStatusCodeException.getStatusCode().value();
                    if (errorCode==503) {
                        log.warn("#775.110 Error accessing url: {}, error: 503 Service Unavailable", url);
                    }
                    else if (errorCode==502) {
                        log.warn("#775.112 Error accessing url: {}, error: 502 Bad Gateway", url);
                    }
                    else {
                        log.error("#775.117 Error accessing url: {}, error: {}", url, e.getMessage());
                    }
                }
                else {
                    log.error("#775.120 Error accessing url: {}", url);
                    log.error("#775.125 Stacktrace", e);
                }
            }
        } catch (Throwable e) {
            log.error("#775.130 Error in fixedDelay(), url: {}, error: {}", serverRestUrl, e.getMessage());
        }
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


