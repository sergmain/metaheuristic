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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 12:49 AM
 */
@Slf4j
public class FunctionRepositoryRequestor {

    private final ProcessorAndCoreData.DispatcherUrl dispatcherUrl;
    private final Globals globals;
    private final ProcessorEnvironment processorEnvironment;

    public FunctionRepositoryRequestor(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, Globals globals, ProcessorEnvironment processorEnvironment) {
        this.dispatcherUrl = dispatcherUrl;
        this.globals = globals;
        this.processorEnvironment = processorEnvironment;
    }

    public void requestFunctionRepository() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

/*
        try {
            FunctionRepositoryRequestParams frrp = new FunctionRepositoryRequestParams();


            final MetadataParamsYaml.ProcessorSession processorSession = processorEnvironment.metadataParams.getProcessorSession(dispatcherUrl);
            final Long processorId = processorSession.processorId;
            final String sessionId = processorSession.sessionId;

            if (processorId == null || sessionId == null) {
                frrp.processor.processorCommContext = null;
            }
            else {
                frrp.processor.processorCommContext = new KeepAliveRequestParamYaml.ProcessorCommContext(processorId, sessionId);
            }

            Set<ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef> cores = processorEnvironment.metadataParams.getAllCoresForDispatcherUrl(dispatcherUrl);
            for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : cores) {
                String coreDir = globals.processorPath.resolve(core.coreCode).toString();
                Long coreId = core.coreId;
                String coreCode = core.coreCode;
                String tags = processorEnvironment.envParams.getTags(core.coreCode);

                frrp.cores.add(new KeepAliveRequestParamYaml.Core(coreDir, coreId, coreCode, tags));
            }

            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);
            frrp.functions.statuses.putAll(processorEnvironment.metadataParams.getAsFunctionDownloadStatuses(assetManagerUrl));

            final String url = dispatcherRestUrl + '/' + R.nextInt(100_000, 1_000_000);
            String yaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(frrp);

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
*/
    }


}
