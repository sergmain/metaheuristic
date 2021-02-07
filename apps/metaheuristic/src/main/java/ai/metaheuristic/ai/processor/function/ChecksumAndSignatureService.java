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

import ai.metaheuristic.ai.processor.MetadataService;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.utils.ProcessorUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager asset,
            String functionCode, ChecksumAndSignatureData.ChecksumWithSignatureInfo checksumState, File functionFile) throws IOException {

        try (FileInputStream fis = new FileInputStream(functionFile)) {
            return getCheckSumAndSignatureStatus(assetManagerUrl, asset, functionCode, checksumState, fis);
        }
    }

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager asset,
            String functionCode, ChecksumAndSignatureData.ChecksumWithSignatureInfo checksumState, InputStream is) {

        CheckSumAndSignatureStatus status;
        status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(
                "Asset url: "+ assetManagerUrl.url +", function: "+functionCode, is, ProcessorUtils.createPublicKey(asset),
                checksumState.originChecksumWithSignature, checksumState.hashAlgo);

        metadataService.setChecksumAndSignatureStatus(assetManagerUrl, functionCode, status);
        return status;
    }
}
