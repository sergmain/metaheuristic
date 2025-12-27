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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:17 PM
 */
@Data
public class ParamsJsonUtilsFactory {

    @NonNull
    public Map<Integer, AbstractParamsJsonUtils> map;

    @NonNull
    public AbstractParamsJsonUtils defJsonUtils;

    public void ParamsYamlUtilsFactory(@NonNull Map<Integer, AbstractParamsJsonUtils> map, @NonNull AbstractParamsJsonUtils defJsonUtils) {
        this.map = map;
        this.defJsonUtils = defJsonUtils;
    }

    @NonNull
    public AbstractParamsJsonUtils getForVersion(int version) {
        AbstractParamsJsonUtils jsonUtils = map.get(version);
        if (jsonUtils==null) {
            throw new WrongVersionOfParamsException("Not supported version: " + version);
        }
        return jsonUtils;
    }

    @NonNull
    public AbstractParamsJsonUtils getDefault() {
        return defJsonUtils;
    }
}
