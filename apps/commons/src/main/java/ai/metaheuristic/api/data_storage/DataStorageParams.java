/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataStorageParams {

    public EnumsApi.DataSourcing sourcing;

    public GitInfo git;

    public DiskInfo disk;

    // TODO this field is the code of resource actually.
    //  Have to be renamed to resourceCode
    public String storageType;

    public DataStorageParams(EnumsApi.DataSourcing sourcing) {
        this.sourcing = sourcing;
    }
}
