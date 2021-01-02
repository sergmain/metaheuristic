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

package ai.metaheuristic.ai.processor.function;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.MetadataService;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.ai.processor.utils.ProcessorUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Serge
 * Date: 1/1/2021
 * Time: 4:18 PM
 */
@Slf4j
@Service
@Profile("processor")
@AllArgsConstructor
public class ChecksumAndSignatureService {

    private final MetadataService metadataService;

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            ProcessorAndCoreData.AssetUrl assetUrl, DispatcherLookupParamsYaml.Asset asset,
            String functionCode, ChecksumAndSignatureData.ChecksumWithSignatureInfo checksumState, File functionFile) throws IOException {

        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus(EnumsApi.ChecksumState.ok, EnumsApi.SignatureState.ok);

        if (checksumState.state!= Enums.SignatureStates.signature_not_required) {
            try (FileInputStream fis = new FileInputStream(functionFile)) {
                status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(
                        "Dispatcher url: "+ dispatcherUrl.url +", function: "+functionCode, fis, ProcessorUtils.createPublicKey(asset),
                        checksumState.originChecksumWithSignature, checksumState.hashAlgo);
            }
            if (status.signature != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.120 function {} has the broken signature", functionCode);
                setFunctionState(assetUrl, functionCode, Enums.FunctionState.signature_wrong);
            }
            else if (status.checksum != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.140 function {} has the wrong checksum", functionCode);
                setFunctionState(assetUrl, functionCode, Enums.FunctionState.checksum_wrong);
            }
        }
        return status;
    }


    public void p() {
        try {
            CheckSumAndSignatureStatus checkSumAndSignatureStatus = getCheckSumAndSignatureStatus(serverUrls.assetUrl,
                    functionCode, simpleCache.dispatcher.dispatcherLookup, checksumState, assetFile.file);

            if (checkSumAndSignatureStatus.checksum == CheckSumAndSignatureStatus.Status.correct && checkSumAndSignatureStatus.signature == CheckSumAndSignatureStatus.Status.correct) {
                if (status.functionState != Enums.FunctionState.ready || !status.verified) {
                    setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.ready);
                }
            }
        } catch (Throwable th) {
            log.error(S.f("#815.100 Error verifying function %s from asset server %s, dispatcher %s", functionCode, serverUrls.assetUrl, serverUrls.dispatcherUrl), th);
            setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.io_error);
        }

    }
}
