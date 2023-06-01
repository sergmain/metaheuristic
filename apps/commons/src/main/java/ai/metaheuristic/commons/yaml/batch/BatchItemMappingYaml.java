/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 8/19/2020
 * Time: 3:39 AM
 */
@Data
public class BatchItemMappingYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    public String targetDir;

    @JsonIgnore
    public Path targetPath;

    // value of key depends on value of field 'key'
    public Map<String, String> filenames = new HashMap<>();

    public EnumsApi.BatchMappingKey key = EnumsApi.BatchMappingKey.id;
}
