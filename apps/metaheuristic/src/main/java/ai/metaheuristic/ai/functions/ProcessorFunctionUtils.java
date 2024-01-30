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

package ai.metaheuristic.ai.functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static ai.metaheuristic.ai.functions.FunctionEnums.ConfigStatus;
import static ai.metaheuristic.ai.functions.FunctionRepositoryData.*;

/**
 * @author Serge
 * Date: 10/6/2019
 * Time: 4:45 PM
 */
@Slf4j
public class ProcessorFunctionUtils {

    @AllArgsConstructor
    @EqualsAndHashCode(of = {"url", "functionCode"})
    public static class AssetManagerUrlAndFunctionCode {
        public final String url;
        public final String functionCode;
    }

    private static final LinkedHashMap<AssetManagerUrlAndFunctionCode, DownloadedFunctionConfigStatus> CACHE = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<AssetManagerUrlAndFunctionCode, DownloadedFunctionConfigStatus> entry) {
            return size()>100;
        }
    };
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public static DownloadedFunctionConfigStatus downloadFunctionConfig(DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {
        final AssetManagerUrlAndFunctionCode key = new AssetManagerUrlAndFunctionCode(assetManager.url, functionCode);
        readLock.lock();
        try {
            DownloadedFunctionConfigStatus status = CACHE.get(key);
            if (status!=null && status.status!=ConfigStatus.error) {
                return status;
            }
        } finally {
            readLock.unlock();
        }

        final DownloadedFunctionConfigStatus downloadedFunctionConfigStatus = downloadFunctionConfigInternal(assetManager, functionCode);

        writeLock.lock();
        try {
            DownloadedFunctionConfigStatus result = CACHE.get(key);
            if (result!=null) {
                return result;
            }
            CACHE.put(key, downloadedFunctionConfigStatus);
            return downloadedFunctionConfigStatus;
        } finally {
            writeLock.unlock();
        }
    }

    private static DownloadedFunctionConfigStatus downloadFunctionConfigInternal(DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {

        // 99999 - fake processorId for backward compatibility
        final String functionConfigUrl = assetManager.url + Consts.REST_ASSET_URL + "/function-config";
//        final String randomPartUri = '/' + UUID.randomUUID().toString().substring(0, 8);
        final String randomPartUri = "";

        final DownloadedFunctionConfigStatus functionConfigStatus = new DownloadedFunctionConfigStatus();
        functionConfigStatus.status = ConfigStatus.error;
        try {
            final URI uri = new URIBuilder(functionConfigUrl + randomPartUri)
                    .setCharset(StandardCharsets.UTF_8)
                    .addParameter("code", functionCode).build();

            final Request request = Request.get(uri).connectTimeout(Timeout.ofSeconds(5));//.socketTimeout(20000);

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(assetManager.url, assetManager.username, assetManager.password).execute(request);
            String yaml = response.returnContent().asString(StandardCharsets.UTF_8);

            final FunctionConfigYaml functionConfigYaml = FunctionConfigYamlUtils.UTILS.to(yaml);
            functionConfigStatus.functionConfig = TaskParamsUtils.toFunctionConfig(functionConfigYaml);
            functionConfigStatus.status = ConfigStatus.ok;

        } catch (HttpResponseException e) {
            if (e.getStatusCode()== HttpServletResponse.SC_FORBIDDEN) {
                log.warn("813.200 Access denied to url {}", functionConfigUrl);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_NOT_FOUND) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("813.220 Url {} wasn't found. Need to check the dispatcher.yaml config file", assetManager.url);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_GONE) {
                functionConfigStatus.status = ConfigStatus.not_found;
                log.warn("813.240 Function with code {} wasn't found", functionCode);
            }
            else if (e.getStatusCode()== HttpServletResponse.SC_CONFLICT) {
                log.warn("813.260 Function with id {} is broken and need to be recreated", functionCode);
            }
            else {
                log.error("813.280 HttpResponseException", e);
            }
        } catch (SocketTimeoutException e) {
            log.error("813.300 SocketTimeoutException: {}, function: {}, assetManagerUrl: {}", e.getMessage(), functionCode, assetManager.url);
        } catch (IOException e) {
            log.error(S.f("813.320 IOException, function: %s, assetManagerUrl: %s",functionCode, assetManager.url), e);
        } catch (Throwable th) {
            log.error(S.f("813.340 Throwable, function: %s, assetManagerUrl: %s",functionCode, assetManager.url), th);
        }
        return functionConfigStatus;
    }

}
