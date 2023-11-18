/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.ProtocolIllegalStateException;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryRequestParamsUtils;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParamsUtils;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 12:49 AM
 */
// TODO p5 2023-11-16 combine with ProcessorKeepAliveRequestor ?
@Slf4j
public class FunctionRepositoryRequestor {

    private final Globals globals;
    private final FunctionRepositoryProcessorService functionRepositoryProcessorService;

    private final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
    private final ProcessorEnvironment processorEnvironment;
    // TODO p5 2023-11-16 do we need to move this to Utils class?
    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();
    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher;
    private final String dispatcherRestUrl;

    private static final Random R = new Random();

    public FunctionRepositoryRequestor(
        ProcessorAndCoreData.DispatcherUrl dispatcherUrl, Globals globals,
        ProcessorEnvironment processorEnvironment, FunctionRepositoryProcessorService functionRepositoryProcessorService) {

        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorEnvironment = processorEnvironment;
        this.functionRepositoryProcessorService = functionRepositoryProcessorService;
        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = this.processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (this.dispatcher == null) {
            throw new IllegalStateException("778.030 Can't find dispatcher config for url " + dispatcherUrl);
        }

        this.dispatcherRestUrl = dispatcherUrl.url + CommonConsts.REST_V1_URL + Consts.FUNCTION_REPOSITORY_REST_URL;
    }

    public void requestFunctionRepository() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        try {
            FunctionRepositoryRequestParams frrp = new FunctionRepositoryRequestParams();
            MetadataParamsYaml.ProcessorSession processorSession = processorEnvironment.metadataParams.getProcessorSession(dispatcherUrl);
            if (processorSession!=null) {
                frrp.processorId = processorSession.processorId;
            }

            final FunctionRepositoryResponseParams responseParams = makeQuery(frrp);
            if (responseParams == null) {
                return;
            }
            FunctionRepositoryRequestParams immediateResponse = functionRepositoryProcessorService.processFunctionRepositoryResponseParams(processorEnvironment, dispatcherUrl, responseParams);
            if (processorSession!=null && immediateResponse!=null) {
                final FunctionRepositoryResponseParams p = makeQuery(immediateResponse);
                if (isNotEmpty(p)) {
                    throw new ProtocolIllegalStateException("778.050 isNotEmpty(p)");
                }
            }

        } catch (Throwable e) {
            log.error("778.060 Error in requestFunctionRepository(), dispatcher url: {}, error: {}", dispatcherRestUrl, e.getMessage());
        }
    }

    public static boolean isNotEmpty(@Nullable FunctionRepositoryResponseParams p) {
        return p!=null && CollectionUtils.isNotEmpty(p.functionCodes);
    }

    @Nullable
    private FunctionRepositoryResponseParams makeQuery(FunctionRepositoryRequestParams frrp) {
        final String url = dispatcherRestUrl + '/' + R.nextInt(100_000, 1_000_000);
        String yaml = FunctionRepositoryRequestParamsUtils.UTILS.toString(frrp);

        final String result = RestUtils.makeRequest(restTemplate, url, yaml, dispatcher.authHeader, dispatcherRestUrl);

        if (result == null) {
            log.warn("778.090 Dispatcher returned null as a result");
            return null;
        }
        FunctionRepositoryResponseParams responseParams = FunctionRepositoryResponseParamsUtils.UTILS.to(result);

        if (!responseParams.success) {
            log.error("778.120 Something wrong at the dispatcher {}. Check the dispatcher's logs for more info.", dispatcherUrl );
            return null;
        }
        return responseParams;
    }


}
