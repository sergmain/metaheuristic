/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.yaml.data_storage;

import aiai.api.v1.EnumsApi;
import aiai.apps.commons.sourcing.DiskInfo;
import aiai.apps.commons.sourcing.GitInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 5/7/2019
 * Time: 9:32 PM
 */
@Data
@NoArgsConstructor
public class DataStorageParams {

    public EnumsApi.DataSourcing sourcing;

    public GitInfo git;

    public DiskInfo disk;

}
