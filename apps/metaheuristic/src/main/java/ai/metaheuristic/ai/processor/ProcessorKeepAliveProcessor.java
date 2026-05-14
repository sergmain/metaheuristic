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

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.secret.SealedSecretCache;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 11:17 AM
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorKeepAliveProcessor {

    private final ProcessorEnvironment processorEnvironment;
    private final CurrentExecState currentExecState;
    private final SealedSecretCache sealedSecretCache;

    public void processKeepAliveResponseParamYaml(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
//        log.debug("776.020 DispatcherCommParamsYaml:\n{}", responseParamYaml);

        storeDispatcherContext(dispatcherUrl, responseParamYaml);
        processExecContextStatus(dispatcherUrl, responseParamYaml.execContextStatus);
//        registerFunctions(dispatcherUrl, responseParamYaml.functions);

        final KeepAliveResponseParamYaml.DispatcherResponse response = responseParamYaml.response;
        ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref = processorEnvironment.getProcessorEnv().metadataParams().getRef(dispatcherUrl);

        if(ref==null) {
            log.warn("ref is null for processorId: {}, dispatcherUrl: {}", response.processorCode, dispatcherUrl);
            return;
        }
        storeProcessorId(dispatcherUrl, response);
        reAssignProcessorId(dispatcherUrl, response);

        storeProcessorCoreId(ref, responseParamYaml.response.coreInfos);

        processVaultInvalidations(response.vaultInvalidations, sealedSecretCache::invalidate);

//        processRequestLogFile(pcpy)
    }

    /**
     * Stage 5b: keep-alive carries any pending Vault-entry invalidations for
     * this Processor. For each entry, drop the matching sealed-secret cache
     * row so the next task launch re-fetches the rotated key.
     *
     * <p>Static helper takes the invalidator as a {@link BiConsumer} —
     * dependency injection by lambda, no Spring context required to test.
     */
    static void processVaultInvalidations(
            @org.jspecify.annotations.Nullable List<KeepAliveResponseParamYaml.VaultEntryInvalidation> invalidations,
            BiConsumer<Long, String> invalidator) {
        if (invalidations == null || invalidations.isEmpty()) {
            return;
        }
        for (KeepAliveResponseParamYaml.VaultEntryInvalidation v : invalidations) {
            if (v == null || v.keyCode == null) {
                continue;
            }
            invalidator.accept(v.companyId, v.keyCode);
        }
    }

    private void storeDispatcherContext(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml responseParamYaml) {
        if (responseParamYaml.dispatcherInfo ==null) {
            return;
        }
        DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher =
                processorEnvironment.getProcessorEnv().dispatcherLookupExtendedService().lookupExtendedMap.get(dispatcherUrl);

        if (dispatcher==null) {
            return;
        }

        int maxVersionOfProcessor = responseParamYaml.dispatcherInfo.processorCommVersion != null
                ? responseParamYaml.dispatcherInfo.processorCommVersion
                : 1;
        DispatcherContextInfoHolder.put(dispatcherUrl, new DispatcherData.DispatcherContextInfo(responseParamYaml.dispatcherInfo.chunkSize, maxVersionOfProcessor));
    }

    private void processExecContextStatus(DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.ExecContextStatus execContextStatus) {
        currentExecState.register(dispatcherUrl, execContextStatus.statuses);
    }

    // processing at processor side
    private void storeProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.assignedProcessorId ==null) {
            return;
        }
        log.info("storeProcessorId() new processor Id: {}", response.assignedProcessorId);
        processorEnvironment.getProcessorEnv().metadataParams().setProcessorIdAndSessionId(
                dispatcherUrl, response.assignedProcessorId.assignedProcessorId.toString(), response.assignedProcessorId.assignedSessionId);
    }

    private void storeProcessorCoreId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, List<KeepAliveResponseParamYaml.CoreInfo> coreInfos) {
        for (KeepAliveResponseParamYaml.CoreInfo coreInfo : coreInfos) {
            processorEnvironment.getProcessorEnv().metadataParams().setCoreId(ref.dispatcherUrl, coreInfo.code, coreInfo.coreId);
        }
    }

    // processing at processor side
    private void reAssignProcessorId(ProcessorAndCoreData.DispatcherUrl dispatcherUrl, KeepAliveResponseParamYaml.DispatcherResponse response) {
        if (response.reAssignedProcessorId ==null) {
            return;
        }
        Long newProcessorId = Long.parseLong(response.reAssignedProcessorId.reAssignedProcessorId);
        final MetadataParamsYaml.ProcessorSession processorSession = processorEnvironment.getProcessorEnv().metadataParams().getProcessorSession(dispatcherUrl);
        final Long currProcessorId = processorSession.processorId;
        final String currSessionId = processorSession.sessionId;
        if (currProcessorId!=null && currSessionId!=null &&
                currProcessorId.equals(newProcessorId) && currSessionId.equals(response.reAssignedProcessorId.sessionId)
        ) {
            return;
        }

        log.info("""
            reAssignProcessorId(),
            \t\tcurrent processorId: {}, sessionId: {}
            \t\tnew processorId: {}, sessionId: {}""",
                currProcessorId, currSessionId,
                response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId
        );
        processorEnvironment.getProcessorEnv().metadataParams().setProcessorIdAndSessionId(
                dispatcherUrl, response.reAssignedProcessorId.getReAssignedProcessorId(), response.reAssignedProcessorId.sessionId);
    }

}
