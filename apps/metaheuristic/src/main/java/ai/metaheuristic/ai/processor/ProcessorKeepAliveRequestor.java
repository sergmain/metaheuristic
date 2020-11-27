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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
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
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

@SuppressWarnings("DuplicatedCode")
@Slf4j
public class ProcessorKeepAliveRequestor {

    private final String dispatcherUrl;
    private final Globals globals;

    private final ProcessorTaskService processorTaskService;
    private final ProcessorService processorService;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorKeepAliveProcessor processorKeepAliveProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();

    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
    private final String serverRestUrl;

    public ProcessorKeepAliveRequestor(
            String dispatcherUrl, Globals globals, ProcessorTaskService processorTaskService,
            ProcessorService processorService, MetadataService metadataService,
            DispatcherLookupExtendedService dispatcherLookupExtendedService, ProcessorKeepAliveProcessor processorKeepAliveProcessor,
            DispatcherRequestorHolderService dispatcherRequestorHolderService) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorTaskService = processorTaskService;
        this.processorService = processorService;
        this.metadataService = metadataService;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
        this.processorKeepAliveProcessor = processorKeepAliveProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = this.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (this.dispatcher == null) {
            throw new IllegalStateException("#776.010 Can'r find dispatcher config for url " + dispatcherUrl);
        }
        this.serverRestUrl = dispatcherUrl + CommonConsts.REST_V1_URL + Consts.KEEP_ALIVE_REST_URL;
    }

    private void processDispatcherCommParamsYaml(KeepAliveRequestParamYaml karpy, String dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        log.debug("#776.020 DispatcherCommParamsYaml:\n{}", responseParamYaml);
        storeDispatcherContext(dispatcherUrl, responseParamYaml);
        processorKeepAliveProcessor.processKeepAliveResponseParamYaml(karpy, dispatcherUrl, responseParamYaml);
    }

    private void storeDispatcherContext(String dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        if (responseParamYaml.dispatcherInfo ==null) {
            return;
        }
        DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        if (dispatcher==null) {
            return;
        }
        dispatcher.context.chunkSize = responseParamYaml.dispatcherInfo.chunkSize;
        dispatcher.context.maxVersionOfProcessor = responseParamYaml.dispatcherInfo.processorCommVersion !=null
            ? responseParamYaml.dispatcherInfo.processorCommVersion
            : 1;
    }

    public void proceedWithRequest() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processorEnabled) {
            return;
        }

        try {
            final String processorId = metadataService.getProcessorId(dispatcherUrl);
            final String sessionId = metadataService.getSessionId(dispatcherUrl);

            KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
            if (processorId == null || sessionId==null) {
                karpy.requestProcessorId = new KeepAliveRequestParamYaml.RequestProcessorId();
            }
            else {
                karpy.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorId, sessionId);
                // always report about current active tasks, if we have actual processorId
                karpy.taskIds = processorTaskService.findAll(dispatcherUrl).stream().map(o -> o.taskId.toString()).collect(Collectors.joining(","));
                karpy.processor = processorService.produceReportProcessorStatus(dispatcherUrl, dispatcher.schedule);
                karpy.functions.statuses.addAll(metadataService.getAsFunctionDownloadStatuses(dispatcherUrl));
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

                String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(karpy);
                HttpEntity<String> request = new HttpEntity<>(yaml, headers);

                log.debug("Start to request a dispatcher at {}", url);
                log.debug("KeepAlive ExchangeData from processor:\n{}", yaml);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                String result = response.getBody();
                log.debug("#776.045 KeepAlive ExchangeData from dispatcher:\n{}", result);
                if (result == null) {
                    log.warn("#776.050 Dispatcher returned null as a result");
                    return;
                }
                KeepAliveResponseParamYaml responseParamYaml = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(result);

                if (!responseParamYaml.success) {
                    log.error("#776.060 Something wrong at the dispatcher {}. Check the dispatcher's logs for more info.", dispatcherUrl );
                    return;
                }
                processDispatcherCommParamsYaml(karpy, dispatcherUrl, responseParamYaml);
            }
            catch (HttpClientErrorException e) {
                switch(e.getStatusCode()) {
                    case UNAUTHORIZED:
                    case FORBIDDEN:
                    case NOT_FOUND:
                        log.error("#775.070 Error {} accessing url {}", e.getStatusCode().value(), serverRestUrl);
                        break;
                    default:
                        throw e;
                }
            }
            catch (ResourceAccessException e) {
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
            }
            catch (RestClientException e) {
                if (e instanceof HttpStatusCodeException && ((HttpStatusCodeException)e).getRawStatusCode()>=500 && ((HttpStatusCodeException)e).getRawStatusCode()<600 ) {
                    int errorCode = ((HttpStatusCodeException)e).getRawStatusCode();
                    if (errorCode==503) {
                        log.error("#775.110 Error accessing url: {}, error: 503 Service Unavailable", url);
                    }
                    else {
                        log.error("#775.110 Error accessing url: {}, error: {}", url, e.getMessage());
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
}

