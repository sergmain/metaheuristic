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
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
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

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.AssetManagerUrl;
import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

/**
 * User: Serg
 * Date: 13.06.2017
 * Time: 16:25
 */

@SuppressWarnings("DuplicatedCode")
@Slf4j
public class ProcessorKeepAliveRequestor {

    private final DispatcherUrl dispatcherUrl;
    private final Globals globals;

    private final ProcessorService processorService;
    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorKeepAliveProcessor processorKeepAliveProcessor;

    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();

    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
    private final String dispatcherRestUrl;

    public ProcessorKeepAliveRequestor(
            DispatcherUrl dispatcherUrl, Globals globals,
            ProcessorService processorService, MetadataService metadataService,
            DispatcherLookupExtendedService dispatcherLookupExtendedService, ProcessorKeepAliveProcessor processorKeepAliveProcessor) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorService = processorService;
        this.metadataService = metadataService;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
        this.processorKeepAliveProcessor = processorKeepAliveProcessor;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = this.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (this.dispatcher == null) {
            throw new IllegalStateException("#776.010 Can't find dispatcher config for url " + dispatcherUrl);
        }
        this.dispatcherRestUrl = dispatcherUrl.url + CommonConsts.REST_V1_URL + Consts.KEEP_ALIVE_REST_URL;
    }

    private void processDispatcherCommParamsYaml(KeepAliveRequestParamYaml karpy, DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        log.debug("#776.020 DispatcherCommParamsYaml:\n{}", responseParamYaml);
        storeDispatcherContext(dispatcherUrl, responseParamYaml);
        processorKeepAliveProcessor.processKeepAliveResponseParamYaml(karpy, dispatcherUrl, responseParamYaml);
    }

    private void storeDispatcherContext(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        if (responseParamYaml.dispatcherInfo ==null) {
            return;
        }
        DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        if (dispatcher==null) {
            return;
        }

        int maxVersionOfProcessor = responseParamYaml.dispatcherInfo.processorCommVersion != null
                ? responseParamYaml.dispatcherInfo.processorCommVersion
                : 1;
        DispatcherContextInfoHolder.put(dispatcherUrl, new DispatcherData.DispatcherContextInfo(responseParamYaml.dispatcherInfo.chunkSize, maxVersionOfProcessor));
    }

    public void proceedWithRequest() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        try {
            KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
            for (ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref : metadataService.getAllEnabledRefs()) {
                if (!ref.dispatcherUrl.equals(dispatcherUrl)) {
                    continue;
                }
                KeepAliveRequestParamYaml.ProcessorRequest request = new KeepAliveRequestParamYaml.ProcessorRequest(ref.processorCode);
                karpy.requests.add(request);

                final String processorId = metadataService.getProcessorId(ref.processorCode, ref.dispatcherUrl);
                final String sessionId = metadataService.getSessionId(ref.processorCode, ref.dispatcherUrl);

                if (processorId == null || sessionId==null) {
                    request.requestProcessorId = new KeepAliveRequestParamYaml.RequestProcessorId();
                    continue;
                }
                final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                        dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

                request.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorId, sessionId);
                request.processor = processorService.produceReportProcessorStatus(ref, dispatcher.schedule);

                AssetManagerUrl assetManagerUrl = new AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);
                karpy.functions.statuses.addAll(metadataService.getAsFunctionDownloadStatuses(assetManagerUrl));
            }

            final String url = dispatcherRestUrl + '/' + UUID.randomUUID().toString().substring(0, 8);
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
                    case BAD_GATEWAY:
                    case SERVICE_UNAVAILABLE:
                        log.error("#776.070 Error {} accessing url {}", e.getStatusCode().value(), dispatcherRestUrl);
                        break;
                    default:
                        throw e;
                }
            }
            catch (ResourceAccessException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SocketException) {
                    log.error("#776.090 Connection error: url: {}, err: {}", url, cause.getMessage());
                }
                else if (cause instanceof UnknownHostException) {
                    log.error("#776.093 Host unreachable, url: {}, error: {}", dispatcherRestUrl, cause.getMessage());
                }
                else if (cause instanceof ConnectTimeoutException) {
                    log.error("#776.093 Connection timeout, url: {}, error: {}", dispatcherRestUrl, cause.getMessage());
                }
                else if (cause instanceof SocketTimeoutException) {
                    log.error("#776.093 Socket timeout, url: {}, error: {}", dispatcherRestUrl, cause.getMessage());
                }
                else if (cause instanceof SSLPeerUnverifiedException) {
                    log.error("#776.093 SSL certificate mismatched, url: {}, error: {}", dispatcherRestUrl, cause.getMessage());
                }
                else {
                    log.error("#776.100 Error, url: " + url, e);
                }
            }
            catch (RestClientException e) {
                if (e instanceof HttpStatusCodeException && ((HttpStatusCodeException)e).getRawStatusCode()>=500 && ((HttpStatusCodeException)e).getRawStatusCode()<600 ) {
                    int errorCode = ((HttpStatusCodeException)e).getRawStatusCode();
                    if (errorCode==502) {
                        log.error("#776.105 Error accessing url: {}, error: 502 Bad Gateway", url);
                    }
                    else if (errorCode==503) {
                        log.error("#776.110 Error accessing url: {}, error: 503 Service Unavailable", url);
                    }
                    else if (errorCode==500) {
                        log.error("#776.111 Error accessing url: {}, error: 500 Internal Server Error", url);
                    }
                    else {
                        log.error("#776.113 Error accessing url: {}, error: {}", url, e.getMessage());
                    }
                }
                else {
                    log.error("#776.120 Error accessing url: {}", url);
                    log.error("#776.125 Stacktrace", e);
                }
            }
        } catch (Throwable e) {
            log.error("#776.130 Error in fixedDelay(), dispatcher url: "+ dispatcherRestUrl +", error: {}", e);
        }
    }
}


