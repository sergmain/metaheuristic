/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.commons.yaml.batch;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 8/19/2020
 * Time: 3:39 AM
 */
@Data
public class BatchItemMappingYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public String targetDir;
    // key is Id of variable
    public Map<String, String> realNames = new HashMap<>();
}
