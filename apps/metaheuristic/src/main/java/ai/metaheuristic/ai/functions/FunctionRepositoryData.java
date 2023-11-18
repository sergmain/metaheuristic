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

import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.replication.ReplicationApiData;
import ai.metaheuristic.commons.utils.threads.EventWithId;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 5:38 PM
 */
public class FunctionRepositoryData {

    @AllArgsConstructor
    @EqualsAndHashCode(of = {"functionCode", "assetManagerUrl"})
    public static class DownloadFunctionTask implements EventWithId<FunctionEnums.DownloadPriority> {
        public final String functionCode;
        public final ProcessorAndCoreData.AssetManagerUrl assetManagerUrl;
        public final boolean signatureRequired;
        public final FunctionEnums.DownloadPriority priority;

        @Override
        public FunctionEnums.DownloadPriority getId() {
            return priority;
        }
    }

    @Data
    public static class FunctionDownloadStatuses {
        public final Map<EnumsApi.FunctionState, String> statuses = new HashMap<>();
    }

    @Data
    public static class FunctionPrepareResult {
        public TaskParamsYaml.FunctionConfig function;
        @Nullable
        public AssetFile functionAssetFile;
        public FunctionApiData.SystemExecResult systemExecResult;
        public boolean isLoaded = true;
        public boolean isError = false;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatus {
        public EnumsApi.FunctionState state;
        public String code;
        public ProcessorAndCoreData.AssetManagerUrl assetManagerUrl;
        public EnumsApi.FunctionSourcing sourcing;

        public EnumsApi.ChecksumState checksum = EnumsApi.ChecksumState.not_yet;
        public EnumsApi.SignatureState signature = EnumsApi.SignatureState.not_yet;

        public final Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public long lastCheck = 0;
    }

    @Data
    @AllArgsConstructor
    public static class FunctionConfigAndStatus {
        @Nullable
        public final TaskParamsYaml.FunctionConfig functionConfig;
        @Nullable
        public final DownloadStatus status;
        @Nullable
        public final AssetFile assetFile;
        public final boolean contentIsInline;

        public FunctionConfigAndStatus(@Nullable DownloadStatus status) {
            this.functionConfig = null;
            this.assetFile = null;
            this.contentIsInline = false;
            this.status = status;
        }

        public FunctionConfigAndStatus(@Nullable TaskParamsYaml.FunctionConfig functionConfig, @Nullable DownloadStatus setFunctionState, AssetFile assetFile) {
            this.functionConfig = functionConfig;
            this.assetFile = assetFile;
            this.contentIsInline = false;
            this.status = setFunctionState;
        }
    }

    @Data
    public static class DownloadedFunctionConfigStatus {
        public TaskParamsYaml.FunctionConfig functionConfig;
        public FunctionEnums.ConfigStatus status;
    }

    @Data
    public static class DownloadedFunctionConfigsStatus {
        public ReplicationApiData.FunctionConfigsReplication functionConfigs;
        public FunctionEnums.ConfigStatus status;
    }
}
