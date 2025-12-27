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

package ai.metaheuristic.api.data_storage;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataStorageParams implements BaseParams {

    public final int version=1;

    // it's a name of asset. Asset can be Variable, GlobalVariable or Function
    // for Variable and GlobalVariable it's a 'name' field
    // for Function it's a 'code' field
    public String name;

    public EnumsApi.DataSourcing sourcing;
    @Nullable
    public GitInfo git;
    @Nullable
    public DiskInfo disk;

    public EnumsApi.@Nullable VariableType type;

    @Nullable
    public Long size = null;

    @Nullable
    public Map<EnumsApi.HashAlgo, String> checksumMap = null;

    public DataStorageParams(EnumsApi.DataSourcing sourcing, String name) {
        this.sourcing = sourcing;
        this.name = name;
    }

    public DataStorageParams(EnumsApi.DataSourcing sourcing, String name, EnumsApi.VariableType type) {
        this.sourcing = sourcing;
        this.name = name;
        this.type = type;
    }
}
