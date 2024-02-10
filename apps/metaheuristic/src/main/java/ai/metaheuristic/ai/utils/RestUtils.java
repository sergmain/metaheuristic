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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.fluent.Request;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.web.client.*;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
public class RestUtils {

    public static void putNoCacheHeaders(Map<String, String> map) {
        map.put("cache-control", "no-cache");
        map.put("expires", "Tue, 01 Jan 1980 1:00:00 GMT");
        map.put("pragma", "no-cache");
    }

    public static void addHeaders(Request request) {
        Map<String, String> map = new HashMap<>();
        putNoCacheHeaders(map);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
    }

    public static HttpHeaders getHeader(long length) {
        return getHeader(null, length);
    }

    public static HttpHeaders getHeader(@Nullable HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }

    @Nullable
    public static String makeRequest(RestTemplate restTemplate, String url, String requestContent, String authHeader, String serverRestUrl) throws InterruptedException {
        String result = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);

            HttpEntity<String> request = new HttpEntity<>(requestContent, headers);

            log.debug("ExchangeData:\n{}", requestContent);
            ThreadUtils.checkInterrupted();
            Thread.sleep(0);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            result = response.getBody();
            log.debug("ExchangeData from dispatcher:\n{}", result);
        } catch (HttpClientErrorException e) {
            int value = e.getStatusCode().value();
            if (value==UNAUTHORIZED.value() || value==FORBIDDEN.value() || value==NOT_FOUND.value()) {
                log.error("775.070 Error {} accessing url {}", e.getStatusCode().value(), serverRestUrl);
            }
            else {
                throw e;
            }
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketException) {
                log.error("775.090 Connection error: url: {}, err: {}", url, cause.getMessage());
            }
            else if (cause instanceof UnknownHostException) {
                log.error("775.093 Host unreachable, url: {}, error: {}", serverRestUrl, cause.getMessage());
            }
            else if (cause instanceof ConnectTimeoutException) {
                log.warn("775.095 Connection timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
            }
            else if (cause instanceof SocketTimeoutException) {
                log.warn("775.097 Socket timeout, url: {}, error: {}", serverRestUrl, cause.getMessage());
            }
            else if (cause instanceof SSLPeerUnverifiedException) {
                log.error("775.098 SSL certificate mismatched, url: {}, error: {}", serverRestUrl, cause.getMessage());
            }
            else if (cause instanceof SSLException) {
                log.error("775.098 SSL error, url: {}, error: {}", serverRestUrl, cause.getMessage());
            }
            else {
                log.error("775.100 Error, url: " + url, e);
            }
            return null;
        } catch (RestClientException e) {
            if (e instanceof HttpStatusCodeException httpStatusCodeException && httpStatusCodeException.getStatusCode().value()>=500 && httpStatusCodeException.getStatusCode().value()<600 ) {
                int errorCode = httpStatusCodeException.getStatusCode().value();
                if (errorCode==503) {
                    log.warn("775.110 Error accessing url: {}, error: 503 Service Unavailable", url);
                }
                else if (errorCode==502) {
                    log.warn("775.112 Error accessing url: {}, error: 502 Bad Gateway", url);
                }
                else {
                    log.error("775.117 Error accessing url: {}, error: {}", url, e.getMessage());
                }
            }
            else {
                log.error("775.120 Error accessing url: {}", url);
                log.error("775.125 Stacktrace", e);
            }
            return null;
        }
        return result;
    }
}
