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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
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

    private final String launchpadUrl;
    private final Globals globals;

    private final StationTaskService stationTaskService;
    private final StationService stationService;
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final StationCommandProcessor stationCommandProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = getHttpRequestFactory();
    private RestTemplate restTemplate;

    private DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad;
    private String serverRestUrl;

    public DispatcherRequestor(String launchpadUrl, Globals globals, StationTaskService stationTaskService, StationService stationService, MetadataService metadataService, CurrentExecState currentExecState, DispatcherLookupExtendedService dispatcherLookupExtendedService, StationCommandProcessor stationCommandProcessor) {
        this.launchpadUrl = launchpadUrl;
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.stationService = stationService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
        this.stationCommandProcessor = stationCommandProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.launchpad = this.dispatcherLookupExtendedService.lookupExtendedMap.get(launchpadUrl);
        if (launchpad == null) {
            throw new IllegalStateException("#775.010 Can'r find launchpad config for url " + launchpadUrl);
        }
        serverRestUrl = launchpadUrl + CommonConsts.REST_V1_URL + Consts.SERVER_REST_URL_V2;
        nextRequest = new StationCommParamsYaml();
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

    private StationCommParamsYaml nextRequest;

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

    private void setRequestStationId(StationCommParamsYaml scpy, final StationCommParamsYaml.RequestStationId requestStationId) {
        withSync(() -> { scpy.requestStationId = requestStationId; return null; });
    }

    private void setStationCommContext(StationCommParamsYaml scpy, StationCommParamsYaml.StationCommContext stationCommContext) {
        withSync(() -> { scpy.stationCommContext = stationCommContext; return null; });
    }

    private void setReportStationStatus(StationCommParamsYaml scpy, StationCommParamsYaml.ReportStationStatus produceReportStationStatus) {
        withSync(() -> { scpy.reportStationStatus = produceReportStationStatus; return null; });
    }

    private void setReportStationTaskStatus(StationCommParamsYaml scpy, StationCommParamsYaml.ReportStationTaskStatus produceStationTaskStatus) {
        withSync(() -> { scpy.reportStationTaskStatus = produceStationTaskStatus; return null; });
    }

    private void setRequestTask(StationCommParamsYaml scpy, StationCommParamsYaml.RequestTask requestTask) {
        withSync(() -> { scpy.requestTask = requestTask; return null; });
    }

    private void setResendTaskOutputResourceResult(StationCommParamsYaml scpy, StationCommParamsYaml.ResendTaskOutputResourceResult resendTaskOutputResourceResult) {
        withSync(() -> { scpy.resendTaskOutputResourceResult = resendTaskOutputResourceResult; return null; });
    }

    private void setCheckForMissingOutputResources(StationCommParamsYaml scpy, StationCommParamsYaml.CheckForMissingOutputResources checkForMissingOutputResources) {
        withSync(() -> { scpy.checkForMissingOutputResources = checkForMissingOutputResources; return null; });
    }

    private void setReportTaskProcessingResult(StationCommParamsYaml scpy, StationCommParamsYaml.ReportTaskProcessingResult reportTaskProcessingResult) {
        withSync(() -> { scpy.reportTaskProcessingResult = reportTaskProcessingResult; return null; });
    }

    private void processLaunchpadCommParamsYaml(StationCommParamsYaml scpy, String launchpadUrl, DispatcherCommParamsYaml launchpadYaml) {
        log.debug("#775.020 DispatcherCommParamsYaml:\n{}", launchpadYaml);
        withSync(() -> {
            storeLaunchpadContext(launchpadUrl, launchpadYaml);
            stationCommandProcessor.processLaunchpadCommParamsYaml(scpy, launchpadUrl, launchpadYaml);
            return null;
        });
    }

    private void storeLaunchpadContext(String launchpadUrl, DispatcherCommParamsYaml dispatcherCommParamsYaml) {
        if (dispatcherCommParamsYaml ==null || dispatcherCommParamsYaml.launchpadCommContext==null) {
            return;
        }
        DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad =
                dispatcherLookupExtendedService.lookupExtendedMap.get(launchpadUrl);

        if (launchpad==null) {
            return;
        }
        storeLaunchpadContext(dispatcherCommParamsYaml, launchpad);
    }

    private void storeLaunchpadContext(DispatcherCommParamsYaml dispatcherCommParamsYaml, DispatcherLookupExtendedService.LaunchpadLookupExtended launchpad) {
        if (dispatcherCommParamsYaml.launchpadCommContext==null) {
            return;
        }
        launchpad.context.chunkSize = dispatcherCommParamsYaml.launchpadCommContext.chunkSize;
        launchpad.context.maxVersionOfStation = dispatcherCommParamsYaml.launchpadCommContext.stationCommVersion!=null
            ? dispatcherCommParamsYaml.launchpadCommContext.stationCommVersion
            : 3;
    }

    private StationCommParamsYaml swap() {
        return getWithSync(() -> {
            StationCommParamsYaml temp = nextRequest;
            nextRequest = new StationCommParamsYaml();
            return temp;
        });
    }

    public void proceedWithRequest() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.isStationEnabled) {
            return;
        }

        try {
            Monitoring.log("##010", Enums.Monitor.MEMORY);
            StationCommParamsYaml scpy = swap();

            final String stationId = metadataService.getStationId(launchpadUrl);
            final String sessionId = metadataService.getSessionId(launchpadUrl);

            if (stationId == null || sessionId==null) {
                setRequestStationId(scpy, new StationCommParamsYaml.RequestStationId());
            }
            else {
                setStationCommContext(scpy, new StationCommParamsYaml.StationCommContext(stationId, sessionId));

                // always report about current active tasks, if we have actual stationId
                setReportStationTaskStatus(scpy, stationTaskService.produceStationTaskStatus(launchpadUrl));
                setReportStationStatus(scpy, stationService.produceReportStationStatus(launchpadUrl, launchpad.schedule));

                // we have to pull new tasks from server constantly
                if (currentExecState.isInited(launchpadUrl)) {
                    Monitoring.log("##011", Enums.Monitor.MEMORY);
                    final boolean b = stationTaskService.isNeedNewTask(launchpadUrl, stationId);
                    Monitoring.log("##012", Enums.Monitor.MEMORY);
                    if (b && !launchpad.schedule.isCurrentTimeInactive()) {
                        setRequestTask(scpy, new StationCommParamsYaml.RequestTask(launchpad.launchpadLookup.acceptOnlySignedFunctions));
                    }
                    else {
                        if (System.currentTimeMillis() - lastCheckForResendTaskOutputResource > 30_000) {
                            // let's check resources for non-completed and not-sending yet tasks
                            List<StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = stationTaskService.findAllByCompletedIsFalse(launchpadUrl).stream()
                                    .filter(t -> t.delivered && t.finishedOn!=null && !t.resourceUploaded)
                                    .map(t->
                                            new StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(
                                                    t.taskId, stationService.resendTaskOutputResource(launchpadUrl, t.taskId)
                                            )
                                    ).collect(Collectors.toList());

                            setResendTaskOutputResourceResult(scpy, new StationCommParamsYaml.ResendTaskOutputResourceResult(statuses));
                            lastCheckForResendTaskOutputResource = System.currentTimeMillis();
                        }
                    }
                }
                if (System.currentTimeMillis() - lastRequestForMissingResources > 15_000) {
                    setCheckForMissingOutputResources(scpy, new StationCommParamsYaml.CheckForMissingOutputResources());
                    lastRequestForMissingResources = System.currentTimeMillis();
                }

                Monitoring.log("##013", Enums.Monitor.MEMORY);
                setReportTaskProcessingResult(scpy, stationTaskService.reportTaskProcessingResult(launchpadUrl));
                Monitoring.log("##014", Enums.Monitor.MEMORY);

                scpy.functionDownloadStatus.statuses.addAll(metadataService.getAsFunctionDownloadStatuses(launchpadUrl));
            }

            final String url = serverRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + stationId;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.setContentType(MediaType.APPLICATION_JSON);

                String auth = launchpad.launchpadLookup.restUsername + ':' + launchpad.launchpadLookup.restPassword;
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);

                String yaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(scpy);
                HttpEntity<String> request = new HttpEntity<>(yaml, headers);
                Monitoring.log("##015", Enums.Monitor.MEMORY);

                log.debug("Start to request a launchpad at {}", url);
                log.debug("ExchangeData:\n{}", yaml);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                Monitoring.log("##016", Enums.Monitor.MEMORY);
                String result = response.getBody();
                log.debug("ExchangeData from launchpad:\n{}", result);
                if (result == null) {
                    log.warn("#775.050 Launchpad returned null as a result");
                    return;
                }
                DispatcherCommParamsYaml launchpadYaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(result);

                if (!launchpadYaml.success) {
                    log.error("#775.060 Something wrong at the launchpad {}. Check the launchpad's logs for more info.", launchpadUrl );
                    return;
                }
                Monitoring.log("##017", Enums.Monitor.MEMORY);
                processLaunchpadCommParamsYaml(scpy, launchpadUrl, launchpadYaml);
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


