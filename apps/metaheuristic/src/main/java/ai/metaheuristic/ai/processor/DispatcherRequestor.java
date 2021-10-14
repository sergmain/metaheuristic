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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.commons.CommonConsts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.*;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

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
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;
    private final ProcessorCommandProcessor processorCommandProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();

    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
    private final String serverRestUrl;

    public DispatcherRequestor(DispatcherUrl dispatcherUrl, Globals globals, ProcessorTaskService processorTaskService, ProcessorService processorService, MetadataService metadataService, CurrentExecState currentExecState, DispatcherLookupExtendedService dispatcherLookupExtendedService, ProcessorCommandProcessor processorCommandProcessor) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.processorService = processorService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.processorCommandProcessor = processorCommandProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (dispatcher == null) {
            throw new IllegalStateException("#775.010 Can't find dispatcher config for url " + dispatcherUrl);
        }
        serverRestUrl = dispatcherUrl.url + CommonConsts.REST_V1_URL + Consts.SERVER_REST_URL_V2;
    }

    private long lastRequestForMissingResources = 0;
    private long lastCheckForResendTaskOutputResource = 0;
    private long lastRequestForData = 0;

    private static class DispatcherRequestorSync {}
    private static final DispatcherRequestorSync syncObj = new DispatcherRequestorSync();

    @SuppressWarnings("unused")
    private static <T> T getWithSync(Supplier<T> function) {
        synchronized (syncObj) {
            return function.get();
        }
    }

    private static void withSync(Supplier<Void> function) {
        synchronized (syncObj) {
            function.get();
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
            for (String processorCode : metadataService.getProcessorCodes()) {

                ProcessorCommParamsYaml.ProcessorRequest r = new ProcessorCommParamsYaml.ProcessorRequest(processorCode);
                pcpy.requests.add(r);

                final String processorId = metadataService.getProcessorId(processorCode, dispatcherUrl);
                final String sessionId = metadataService.getSessionId(processorCode, dispatcherUrl);

                if (processorId == null || sessionId == null) {
                    r.requestProcessorId = new ProcessorCommParamsYaml.RequestProcessorId();
                    continue;
                }
                ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(processorCode, processorId, dispatcherUrl);
                r.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorId, sessionId);

                // we have to pull new tasks from server constantly
                if (currentExecState.isInited(dispatcherUrl)) {
                    final boolean b = processorTaskService.isNeedNewTask(ref);
                    if (b && dispatcher.schedule.isCurrentTimeActive()) {
                        r.requestTask = new ProcessorCommParamsYaml.RequestTask(true, dispatcher.dispatcherLookup.signatureRequired);
                    } else {
                        if (System.currentTimeMillis() - lastCheckForResendTaskOutputResource > 30_000) {
                            // let's check variables for not completed and not sent yet tasks
                            List<ProcessorTask> processorTasks = processorTaskService.findAllByCompletedIsFalse(ref).stream()
                                    .filter(t -> t.delivered && t.finishedOn != null && !t.output.allUploaded())
                                    .collect(Collectors.toList());

                            List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = new ArrayList<>();
                            for (ProcessorTask processorTask : processorTasks) {
                                for (ProcessorTask.OutputStatus outputStatus : processorTask.output.outputStatuses) {
                                    statuses.add(new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(
                                            processorTask.taskId, outputStatus.variableId,
                                            processorService.resendTaskOutputResources(ref, processorTask.taskId, outputStatus.variableId))
                                    );
                                }
                            }

                            r.resendTaskOutputResourceResult = new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses);
                            lastCheckForResendTaskOutputResource = System.currentTimeMillis();
                        }
                    }
                }
                if (System.currentTimeMillis() - lastRequestForMissingResources > 30_000) {
                    r.checkForMissingOutputResources = new ProcessorCommParamsYaml.CheckForMissingOutputResources();
                    lastRequestForMissingResources = System.currentTimeMillis();
                }
                r.reportTaskProcessingResult = processorTaskService.reportTaskProcessingResult(ref);
            }
            if (System.currentTimeMillis() - lastRequestForData > globals.data.getSyncTimeout().toMillis()) {
                pcpy.dataSource = new ProcessorCommParamsYaml.DataSource(globals.data.primary, null);
                lastRequestForData = System.currentTimeMillis();
            }
            if (!newRequest(pcpy)) {
                log.info("#775.045 no new requests");
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
                switch(e.getStatusCode()) {
                    case UNAUTHORIZED:
                    case FORBIDDEN:
                    case NOT_FOUND:
                        log.error("#775.070 Error {} accessing url {}", e.getStatusCode().value(), serverRestUrl);
                        break;
                    default:
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
                else {
                    log.error("#775.100 Error, url: " + url, e);
                }
            } catch (RestClientException e) {
                if (e instanceof HttpStatusCodeException && ((HttpStatusCodeException)e).getRawStatusCode()>=500 && ((HttpStatusCodeException)e).getRawStatusCode()<600 ) {
                    int errorCode = ((HttpStatusCodeException)e).getRawStatusCode();
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
            log.error("#775.130 Error in fixedDelay(), url: "+serverRestUrl+", error: {}", e);
        }
    }

    private static boolean newRequest(ProcessorCommParamsYaml pcpy) {
        for (ProcessorCommParamsYaml.ProcessorRequest request : pcpy.requests) {
            boolean state = request.requestProcessorId!=null || request.requestTask!=null ||
                    request.reportTaskProcessingResult!=null || request.checkForMissingOutputResources!=null ||
                    request.resendTaskOutputResourceResult!=null;
            if (state) {
                return true;
            }
        }
        return pcpy.dataSource!=null;
    }

}


