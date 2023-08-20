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

package ai.metaheuristic.ai.processor.function;

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.ai.processor.utils.ProcessorUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;

/**
 * @author Serge
 * Date: 1/1/2021
 * Time: 4:18 PM
 */
@Slf4j
@Service
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ChecksumAndSignatureService {

    private final ProcessorEnvironment processorEnvironment;

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager asset,
            String functionCode, ChecksumAndSignatureData.ChecksumWithSignatureInfo checksumState, Path functionFile) throws IOException {

        try (InputStream fis = Files.newInputStream(functionFile)) {
            return getCheckSumAndSignatureStatus(assetManagerUrl, asset, functionCode, checksumState, fis);
        }
    }

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager asset,
            String functionCode, ChecksumAndSignatureData.ChecksumWithSignatureInfo checksumState, InputStream is) {

        CheckSumAndSignatureStatus status;
        final PublicKey publicKey = asset.publicKey!=null ? ProcessorUtils.createPublicKey(asset) : null;
        status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(
                "Asset url: "+ assetManagerUrl.url +", function: "+functionCode, is, publicKey,
                checksumState.originChecksumWithSignature, checksumState.hashAlgo);

        processorEnvironment.metadataParams.setChecksumAndSignatureStatus(assetManagerUrl, functionCode, status);
        return status;
    }
}
