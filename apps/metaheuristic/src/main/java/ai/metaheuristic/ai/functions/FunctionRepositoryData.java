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

import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 11/16/2023
 * Time: 5:38 PM
 */
public class FunctionRepositoryData {

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
}
