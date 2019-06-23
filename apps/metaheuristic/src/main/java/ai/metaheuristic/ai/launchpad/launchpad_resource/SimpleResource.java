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

package ai.metaheuristic.ai.launchpad.launchpad_resource;

import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleResource {
    private Long id;
    private Integer version;
    private String code;
    private String poolCode;
    private int dataType;
    private java.util.Date uploadTs;
    public String checksum;
    public boolean valid;
    public boolean manual;
    public String filename;
    public String storageUrl;

    public String getDataTypeAsStr() {
        return EnumsApi.BinaryDataType.from(dataType).toString();
    }
}