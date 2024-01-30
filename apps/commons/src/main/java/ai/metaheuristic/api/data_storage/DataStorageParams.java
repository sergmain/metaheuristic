/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.annotation.Nullable;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataStorageParams {

    // it's a name of asset. Asset can be Variable, GlobalVariable or Function
    // for Variable and GlobalVariable it's a 'name' field
    // for Function it's a 'code' field
    public String name;

    public EnumsApi.DataSourcing sourcing;
    @Nullable
    public GitInfo git;
    @Nullable
    public DiskInfo disk;

    @Nullable
    public EnumsApi.VariableType type;

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
