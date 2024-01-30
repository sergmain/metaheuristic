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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import lombok.Data;
import javax.annotation.Nonnull;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:17 PM
 */
@Data
public class ParamsJsonUtilsFactory {

    @Nonnull
    public Map<Integer, AbstractParamsJsonUtils> map;

    @Nonnull
    public AbstractParamsJsonUtils defJsonUtils;

    public void ParamsYamlUtilsFactory(@Nonnull Map<Integer, AbstractParamsJsonUtils> map, @Nonnull AbstractParamsJsonUtils defJsonUtils) {
        this.map = map;
        this.defJsonUtils = defJsonUtils;
    }

    @Nonnull
    public AbstractParamsJsonUtils getForVersion(int version) {
        AbstractParamsJsonUtils jsonUtils = map.get(version);
        if (jsonUtils==null) {
            throw new WrongVersionOfParamsException("Not supported version: " + version);
        }
        return jsonUtils;
    }

    @Nonnull
    public AbstractParamsJsonUtils getDefault() {
        return defJsonUtils;
    }
}
