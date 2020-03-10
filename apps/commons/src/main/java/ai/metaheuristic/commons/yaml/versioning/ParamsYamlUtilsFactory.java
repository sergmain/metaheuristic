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

package ai.metaheuristic.commons.yaml.versioning;

import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import lombok.Data;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:24 PM
 */
@SuppressWarnings("rawtypes")
@Data
public class ParamsYamlUtilsFactory {

    public @NonNull Map<Integer, AbstractParamsYamlUtils> map;
    public @NonNull AbstractParamsYamlUtils defYamlUtils;

    public void ParamsYamlUtilsFactory(@NonNull Map<Integer, AbstractParamsYamlUtils> map, @NonNull AbstractParamsYamlUtils defYamlUtils) {
        this.map = map;
        this.defYamlUtils = defYamlUtils;
    }

    public @NonNull AbstractParamsYamlUtils getForVersion(int version) {
        AbstractParamsYamlUtils yamlUtils = map.get(version);
        if (yamlUtils==null) {
            throw new WrongVersionOfYamlFileException("Not supported version: " + version);
        }
        return yamlUtils;
    }


    public @NonNull AbstractParamsYamlUtils getDefault() {
        return defYamlUtils;
    }
}
