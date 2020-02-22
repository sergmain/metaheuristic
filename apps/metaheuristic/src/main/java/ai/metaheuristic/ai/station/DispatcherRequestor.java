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
import ai.metaheuristic.ai.yaml.communication.mh.dispatcher..DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.mh.dispatcher..DispatcherCommParamsYamlUtils;
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

    private final String mh.dispatcher.Url;
    private final Globals globals;

    private final StationTaskService stationTaskService;
    private final StationService stationService;
    private final MetadataService metadataService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService;
    private final StationCommandProcessor stationCommandProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = getHttpRequestFactory();
    private RestTemplate restTemplate;

    private DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher.;
    private String serverRestUrl;

    public DispatcherRequestor(String mh.dispatcher.Url, Globals globals, StationTaskService stationTaskService, StationService stationService, MetadataService metadataService, CurrentExecState currentExecState, DispatcherLookupExtendedService mh.dispatcher.LookupExtendedService, StationCommandProcessor stationCommandProcessor) {
        this.mh.dispatcher.Url = mh.dispatcher.Url;
        this.globals = globals;
        this.stationTaskService = stationTaskService;
        this.stationService = stationService;
        this.metadataService = metadataService;
        this.currentExecState = currentExecState;
        this.mh.dispatcher.LookupExtendedService = mh.dispatcher.LookupExtendedService;
        this.stationCommandProcessor = stationCommandProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.mh.dispatcher. = this.mh.dispatcher.LookupExtendedService.lookupExtendedMap.get(mh.dispatcher.Url);
        if (mh.dispatcher. == null) {
            throw new IllegalStateException("#775.010 Can'r find mh.dispatcher. config for url " + mh.dispatcher.Url);
        }
        serverRestUrl = mh.dispatcher.Url + CommonConsts.REST_V1_URL + Consts.SERVER_REST_URL_V2;
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

    private void processLaunchpadCommParamsYaml(StationCommParamsYaml scpy, String mh.dispatcher.Url, DispatcherCommParamsYaml mh.dispatcher.Yaml) {
        log.debug("#775.020 DispatcherCommParamsYaml:\n{}", mh.dispatcher.Yaml);
        withSync(() -> {
            storeLaunchpadContext(mh.dispatcher.Url, mh.dispatcher.Yaml);
            stationCommandProcessor.processLaunchpadCommParamsYaml(scpy, mh.dispatcher.Url, mh.dispatcher.Yaml);
            return null;
        });
    }

    private void storeLaunchpadContext(String mh.dispatcher.Url, DispatcherCommParamsYaml mh.dispatcher.CommParamsYaml) {
        if (mh.dispatcher.CommParamsYaml ==null || mh.dispatcher.CommParamsYaml.mh.dispatcher.CommContext ==null) {
            return;
        }
        DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher. =
                mh.dispatcher.LookupExtendedService.lookupExtendedMap.get(mh.dispatcher.Url);

        if (mh.dispatcher.==null) {
            return;
        }
        storeLaunchpadContext(mh.dispatcher.CommParamsYaml, mh.dispatcher.);
    }

    private void storeLaunchpadContext(DispatcherCommParamsYaml mh.dispatcher.CommParamsYaml, DispatcherLookupExtendedService.DispatcherLookupExtended mh.dispatcher.) {
        if (mh.dispatcher.CommParamsYaml.mh.dispatcher.CommContext ==null) {
            return;
        }
        mh.dispatcher..context.chunkSize = mh.dispatcher.CommParamsYaml.mh.dispatcher.CommContext.chunkSize;
        mh.dispatcher..context.maxVersionOfStation = mh.dispatcher.CommParamsYaml.mh.dispatcher.CommContext.stationCommVersion!=null
            ? mh.dispatcher.CommParamsYaml.mh.dispatcher.CommContext.stationCommVersion
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

            final String stationId = metadataService.getStationId(mh.dispatcher.Url);
            final String sessionId = metadataService.getSessionId(mh.dispatcher.Url);

            if (stationId == null || sessionId==null) {
                setRequestStationId(scpy, new StationCommParamsYaml.RequestStationId());
            }
            else {
                setStationCommContext(scpy, new StationCommParamsYaml.StationCommContext(stationId, sessionId));

                // always report about current active tasks, if we have actual stationId
                setReportStationTaskStatus(scpy, stationTaskService.produceStationTaskStatus(mh.dispatcher.Url));
                setReportStationStatus(scpy, stationService.produceReportStationStatus(mh.dispatcher.Url, mh.dispatcher..schedule));

                // we have to pull new tasks from server constantly
                if (currentExecState.isInited(mh.dispatcher.Url)) {
                    Monitoring.log("##011", Enums.Monitor.MEMORY);
                    final boolean b = stationTaskService.isNeedNewTask(mh.dispatcher.Url, stationId);
                    Monitoring.log("##012", Enums.Monitor.MEMORY);
                    if (b && !mh.dispatcher..schedule.isCurrentTimeInactive()) {
                        setRequestTask(scpy, new StationCommParamsYaml.RequestTask(mh.dispatcher..mh.dispatcher.Lookup.acceptOnlySignedFunctions));
                    }
                    else {
                        if (System.currentTimeMillis() - lastCheckForResendTaskOutputResource > 30_000) {
                            // let's check resources for non-completed and not-sending yet tasks
                            List<StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus> statuses = stationTaskService.findAllByCompletedIsFalse(mh.dispatcher.Url).stream()
                                    .filter(t -> t.delivered && t.finishedOn!=null && !t.resourceUploaded)
                                    .map(t->
                                            new StationCommParamsYaml.ResendTaskOutputResourceResult.SimpleStatus(
                                                    t.taskId, stationService.resendTaskOutputResource(mh.dispatcher.Url, t.taskId)
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
                setReportTaskProcessingResult(scpy, stationTaskService.reportTaskProcessingResult(mh.dispatcher.Url));
                Monitoring.log("##014", Enums.Monitor.MEMORY);

                scpy.functionDownloadStatus.statuses.addAll(metadataService.getAsFunctionDownloadStatuses(mh.dispatcher.Url));
            }

            final String url = serverRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8) + '-' + stationId;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.setContentType(MediaType.APPLICATION_JSON);

                String auth = mh.dispatcher..mh.dispatcher.Lookup.restUsername + ':' + mh.dispatcher..mh.dispatcher.Lookup.restPassword;
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);

                String yaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(scpy);
                HttpEntity<String> request = new HttpEntity<>(yaml, headers);
                Monitoring.log("##015", Enums.Monitor.MEMORY);

                log.debug("Start to request a mh.dispatcher. at {}", url);
                log.debug("ExchangeData:\n{}", yaml);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                Monitoring.log("##016", Enums.Monitor.MEMORY);
                String result = response.getBody();
                log.debug("ExchangeData from mh.dispatcher.:\n{}", result);
                if (result == null) {
                    log.warn("#775.050 Launchpad returned null as a result");
                    return;
                }
                DispatcherCommParamsYaml mh.dispatcher.Yaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(result);

                if (!mh.dispatcher.Yaml.success) {
                    log.error("#775.060 Something wrong at the mh.dispatcher. {}. Check the mh.dispatcher.'s logs for more info.", mh.dispatcher.Url );
                    return;
                }
                Monitoring.log("##017", Enums.Monitor.MEMORY);
                processLaunchpadCommParamsYaml(scpy, mh.dispatcher.Url, mh.dispatcher.Yaml);
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


