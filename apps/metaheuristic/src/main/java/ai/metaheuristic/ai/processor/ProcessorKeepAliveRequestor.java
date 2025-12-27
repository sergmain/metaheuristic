/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.utils.DispatcherUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;

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
    private final ProcessorKeepAliveProcessor processorKeepAliveProcessor;
    private final ProcessorEnvironment processorEnvironment;

    // TODO p5 2023-11-16 do we need to move this to Utils class?
    private static final HttpComponentsClientHttpRequestFactory REQUEST_FACTORY = DispatcherUtils.getHttpRequestFactory();

    private final RestTemplate restTemplate;
    private final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher;
    private final String dispatcherRestUrl;

    private static final Random R = new Random();

    public ProcessorKeepAliveRequestor(
            DispatcherUrl dispatcherUrl, Globals globals,
            ProcessorService processorService, ProcessorKeepAliveProcessor processorKeepAliveProcessor, ProcessorEnvironment processorEnvironment) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorService = processorService;
        this.processorKeepAliveProcessor = processorKeepAliveProcessor;
        this.processorEnvironment = processorEnvironment;

        this.restTemplate = new RestTemplate(REQUEST_FACTORY);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.dispatcher = this.processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);
        if (this.dispatcher == null) {
            throw new IllegalStateException("776.010 Can't find dispatcher config for url " + dispatcherUrl);
        }
        this.dispatcherRestUrl = dispatcherUrl.url + CommonConsts.REST_V1_URL + Consts.KEEP_ALIVE_REST_URL;
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
            karpy.processor.status = processorService.produceReportProcessorStatus(dispatcher.schedule);


            final MetadataParamsYaml.ProcessorSession processorSession = processorEnvironment.metadataParams.getProcessorSession(dispatcherUrl);
            final Long processorId = processorSession.processorId;
            final String sessionId = processorSession.sessionId;

            if (processorId == null || sessionId == null) {
                karpy.processor.processorCommContext = null;
            }
            else {
                karpy.processor.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorId, sessionId);
            }

            Set<ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef> cores = processorEnvironment.metadataParams.getAllCoresForDispatcherUrl(dispatcherUrl);
            for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : cores) {
                String coreDir = globals.processorPath.resolve(core.coreCode).toString();
                Long coreId = core.coreId;
                String coreCode = core.coreCode;
                String tags = processorEnvironment.envParams.getTags(core.coreCode);

                karpy.cores.add(new KeepAliveRequestParamYaml.Core(coreDir, coreId, coreCode, tags));
            }

            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                    processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

            final String url = dispatcherRestUrl + '/' + R.nextInt(100_000, 1_000_000);
            String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(karpy);

            final String result = RestUtils.makeRequest(restTemplate, url, yaml, dispatcher.authHeader, dispatcherRestUrl);

            if (result == null) {
                log.warn("776.050 Dispatcher returned null as a result");
                return;
            }
            KeepAliveResponseParamYaml responseParamYaml = KeepAliveResponseParamYamlUtils.BASE_YAML_UTILS.to(result);

            if (!responseParamYaml.success) {
                log.error("776.060 Something wrong at the dispatcher {}. Check the dispatcher's logs for more info.", dispatcherUrl );
                return;
            }
            processorKeepAliveProcessor.processKeepAliveResponseParamYaml(dispatcherUrl, responseParamYaml);

        } catch (Throwable e) {
            log.error("776.130 Error in fixedDelay(), dispatcher url: {}, error: {}", dispatcherRestUrl, e.getMessage());
        }
    }
}


