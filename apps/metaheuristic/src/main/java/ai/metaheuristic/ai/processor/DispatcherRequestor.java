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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.commons.CommonConsts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.*;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.http.client.config.RequestConfig.custom;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

@Slf4j
public class DispatcherRequestor {

    private final String dispatcherUrl;
    private final Globals globals;

    private final ProcessorTaskService processorTaskService;
    private final ProcessorService processorService;
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorCommandProcessor processorCommandProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = getHttpRequestFactory();
    private RestTemplate restTemplate;

    private DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
    private String serverRestUrl;

    public DispatcherRequestor(String dispatcherUrl, Globals globals, ProcessorTaskService processorTaskService, ProcessorService processorService, MetadataService metadataService, CurrentExecState currentExecState, DispatcherLookupExtendedService dispatcherLookupExtendedService, ProcessorCommandProcessor processorCommandProcessor) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.processorService = processorService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
        this.processorCommandProcessor = processorCommandProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = this.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (dispatcher == null) {
            throw new IllegalStateException("#775.010 Can'r find dispatcher config for url " + dispatcherUrl);
        }
        serverRestUrl = dispatcherUrl + CommonConsts.REST_V1_URL + Consts.SERVER_REST_URL_V2;
        nextRequest = new ProcessorCommParamsYaml();
    }

    public static HttpComponentsClientHttpRequestFactory getHttpRequestFactory() {
        // https://github.com/spring-projects/spring-boot/issues/11379
        // https://issues.apache.org/jira/browse/HTTPCLIENT-1892
        // https://github.com/spring-projects/spring-framework/issues/21238

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout((int) Duration.ofSeconds(5).toMillis()).build();

        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setDefaultSocketConfig(socketConfig);
        clientBuilder.useSystemProperties();
        clientBuilder.setDefaultRequestConfig(custom().setConnectTimeout((int) Duration.ofSeconds(5).toMillis())
                .setSocketTimeout((int) Duration.ofSeconds(5).toMillis())
                .build());
        final HttpClient httpClient = clientBuilder.build();

        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return requestFactory;
    }

    private long lastRequestForMissingResources = 0;
    private long lastCheckForResendTaskOutputResource = 0;

    private ProcessorCommParamsYaml nextRequest;

    private static final Object syncObj = new Object();
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

    private void setRequestProcessorId(ProcessorCommParamsYaml scpy, final ProcessorCommParamsYaml.RequestProcessorId requestProcessorId) {
        withSync(() -> { scpy.requestProcessorId = requestProcessorId; return null; });
    }

    private void setProcessorCommContext(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.ProcessorCommContext processorCommContext) {
        withSync(() -> { scpy.processorCommContext = processorCommContext; return null; });
    }

    private void setReportProcessorStatus(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.ReportProcessorStatus produceReportProcessorStatus) {
        withSync(() -> { scpy.reportProcessorStatus = produceReportProcessorStatus; return null; });
    }

    private void setReportProcessorTaskStatus(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.ReportProcessorTaskStatus produceProcessorTaskStatus) {
        withSync(() -> { scpy.reportProcessorTaskStatus = produceProcessorTaskStatus; return null; });
    }

    private void setRequestTask(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.RequestTask requestTask) {
        withSync(() -> { scpy.requestTask = requestTask; return null; });
    }

    private void setResendTaskOutputResourceResult(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResourceResult) {
        withSync(() -> { scpy.resendTaskOutputResourceResult = resendTaskOutputResourceResult; return null; });
    }

    private void setCheckForMissingOutputResources(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.CheckForMissingOutputResources checkForMissingOutputResources) {
        withSync(() -> { scpy.checkForMissingOutputResources = checkForMissingOutputResources; return null; });
    }

    private void setReportTaskProcessingResult(ProcessorCommParamsYaml scpy, ProcessorCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult) {
        withSync(() -> { scpy.reportTaskProcessingResult = reportTaskProcessingResult; return null; });
    }

    private void processDispatcherCommParamsYaml(ProcessorCommParamsYaml scpy, String dispatcherUrl, DispatcherCommParamsYaml dispatcherYaml) {
        log.debug("#775.020 DispatcherCommParamsYaml:\n{}", dispatcherYaml);
        withSync(() -> {
            storeDispatcherContext(dispatcherUrl, dispatcherYaml);
            processorCommandProcessor.processDispatcherCommParamsYaml(scpy, dispatcherUrl, dispatcherYaml);
            return null;
        });
    }

    private void storeDispatcherContext(String dispatcherUrl, DispatcherCommParamsYaml dispatcherCommParamsYaml) {
        if (dispatcherCommParamsYaml ==null || dispatcherCommParamsYaml.dispatcherCommContext ==null) {
            return;
        }
        DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        if (dispatcher==null) {
            return;
        }
        storeDispatcherContext(dispatcherCommParamsYaml, dispatcher);
    }

    private void storeDispatcherContext(DispatcherCommParamsYaml dispatcherCommParamsYaml, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher) {
        if (dispatcherCommParamsYaml.dispatcherCommContext ==null) {
            return;
        }
        dispatcher.context.chunkSize = dispatcherCommParamsYaml.dispatcherCommContext.chunkSize;
        dispatcher.context.maxVersionOfProcessor = dispatcherCommParamsYaml.dispatcherCommContext.processorCommVersion !=null
            ? dispatcherCommParamsYaml.dispatcherCommContext.processorCommVersion
            : 3;
    }

    private ProcessorCommParamsYaml swap() {
        return getWithSync(() -> {
            ProcessorCommParamsYaml temp = nextRequest;
            nextRequest = new ProcessorCommParamsYaml();
            return temp;
        });
    }

    public void proceedWithRequest() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        try {
            Monitoring.log("##010", Enums.Monitor.MEMORY);
            ProcessorCommParamsYaml scpy = swap();

            final String processorId = metadataService.getProcessorId(dispatcherUrl);
            final String sessionId = metadataService.getSessionId(dispatcherUrl);

            if (processorId == null || sessionId==null) {
                setRequestProcessorId(scpy, new ProcessorCommParamsYaml.RequestProcessorId());
            }
            else {
                setProcessorCommContext(scpy, new ProcessorCommParamsYaml.ProcessorCommContext(processorId, sessionId));

                // always report about current active tasks, if we have actual processorId
                setReportProcessorTaskStatus(scpy, processorTaskService.produceProcessorTaskStatus(dispatcherUrl));
                setReportProcessorStatus(scpy, processorService.produceReportProcessorStatus(dispatcherUrl, dispatcher.schedule));

                // we have to pull new tasks from server constantly
                if (currentExecState.isInited(dispatcherUrl)) {
                    Monitoring.log("##011", Enums.Monitor.MEMORY);
                    final boolean b = processorTaskService.isNeedNewTask(dispatcherUrl, processorId);
                    Monitoring.log("##012", Enums.Monitor.MEMORY);
                    if (b && dispatcher.schedule.isCurrentTimeActive()) {
                        setRequestTask(scpy, new ProcessorCommParamsYaml.RequestTask(dispatcher.dispatcherLookup.acceptOnlySignedFunctions));
                    }
                    else {
                        if (System.currentTimeMillis() - lastCheckForResendTaskOutputResource > 30_000) {
                            // let's check resources for not completed and not sended yet tasks
                            List<ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = processorTaskService.findAllByCompletedIsFalse(dispatcherUrl).stream()
                                    .filter(t -> t.delivered && t.finishedOn!=null && !t.outputStatuses.allUploaded())
                                    .map(t->
                                            new ProcessorCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(
                                                    t.taskId, processorService.resendTaskOutputResources(dispatcherUrl, t.taskId)
                                            )
                                    ).collect(Collectors.toList());

                            setResendTaskOutputResourceResult(scpy, new ProcessorCommParamsYaml.ResendTaskOutputResourceResult(statuses));
                            lastCheckForResendTaskOutputResource = System.currentTimeMillis();
                        }
                    }
                }
                if (System.currentTimeMillis() - lastRequestForMissingResources > 15_000) {
                    setCheckForMissingOutputResources(scpy, new ProcessorCommParamsYaml.CheckForMissingOutputResources());
                    lastRequestForMissingResources = System.currentTimeMillis();
                }

                Monitoring.log("##013", Enums.Monitor.MEMORY);
                setReportTaskProcessingResult(scpy, processorTaskService.reportTaskProcessingResult(dispatcherUrl));
                Monitoring.log("##014", Enums.Monitor.MEMORY);

                scpy.functionDownloadStatus.statuses.addAll(metadataService.getAsFunctionDownloadStatuses(dispatcherUrl));
            }

            final String url = serverRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + processorId;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.setContentType(MediaType.APPLICATION_JSON);

                String auth = dispatcher.dispatcherLookup.restUsername + ':' + dispatcher.dispatcherLookup.restPassword;
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);

                String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(scpy);
                HttpEntity<String> request = new HttpEntity<>(yaml, headers);
                Monitoring.log("##015", Enums.Monitor.MEMORY);

                log.debug("Start to request a dispatcher at {}", url);
                log.debug("ExchangeData:\n{}", yaml);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                Monitoring.log("##016", Enums.Monitor.MEMORY);
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
                Monitoring.log("##017", Enums.Monitor.MEMORY);
                processDispatcherCommParamsYaml(scpy, dispatcherUrl, dispatcherYaml);
                Monitoring.log("##018", Enums.Monitor.MEMORY);
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
                    log.error("#775.093 Connection timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof SocketTimeoutException) {
                    log.error("#775.093 Socket timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else if (cause instanceof SSLPeerUnverifiedException) {
                    log.error("#775.093 SSL certificate mismatched, url: {}, error: {}", serverRestUrl, cause.getMessage());
                }
                else {
                    log.error("#775.100 Error, url: " + url, e);
                }
            } catch (RestClientException e) {
                log.error("#775.110 Error accessing url: {}, error: {}", url, e.getMessage());

                //noinspection StatementWithEmptyBody
                if (e instanceof HttpStatusCodeException && ((HttpStatusCodeException)e).getRawStatusCode()>=500 & ((HttpStatusCodeException)e).getRawStatusCode()<600 ) {
                    // short info for all 5xx errors
                }
                else {
                    log.error("#775.120 Stacktrace", e);
                }
            }
        } catch (Throwable e) {
            log.error("#775.130 Error in fixedDelay(), url: "+serverRestUrl+", error: {}", e);
        }
    }
}


