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
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.YamlVersion;
import lombok.NonNull;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:58 PM
 */
public class BaseYamlUtils<T> {

    private final ParamsYamlUtilsFactory FACTORY = new ParamsYamlUtilsFactory();

    public BaseYamlUtils(Map<Integer, AbstractParamsYamlUtils> map, AbstractParamsYamlUtils defYamlUtils) {
        FACTORY.map = map;
        map.forEach((k,v)-> {
            if (k!=v.getVersion()) {
                throw new IllegalStateException("Version is different, class: " + v.getClass().toString());
            }
        });
        FACTORY.defYamlUtils = defYamlUtils;
    }

    public AbstractParamsYamlUtils getForVersion(int version) {
        return FACTORY.getForVersion(version);
    }

    public AbstractParamsYamlUtils getDefault() {
        return FACTORY.getDefault();
    }

    public String toString(BaseParams planYaml) {
        return getDefault().getYaml().dumpAsMap(planYaml);
    }

    public String toStringAsVersion(BaseParams baseParamsYaml, int version) {
        if (getDefault().getVersion()==version) {
            return toString(baseParamsYaml);
        }
        else {

            AbstractParamsYamlUtils yamlUtils = getDefault();
            Object currPlanParamsYaml = baseParamsYaml;
            do {
                if (yamlUtils.getVersion()==version) {
                    break;
                }
                //noinspection unchecked
                currPlanParamsYaml = yamlUtils.downgradeTo(currPlanParamsYaml);
            } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.prevUtil())!=null);

            //noinspection unchecked
            T p = (T)currPlanParamsYaml;

            return getForVersion(version).getYaml().dumpAsMap(p);
        }
    }

    public T to(@NonNull String s, Long ... vars) {
        try {
            YamlVersion v = YamlForVersioning.getYamlForVersion().load(s);
            AbstractParamsYamlUtils yamlUtils = getForVersion(v.getActualVersion());
            BaseParams currPlanParamsYaml = yamlUtils.to(s);
            do {
                //noinspection unchecked
                currPlanParamsYaml = yamlUtils.upgradeTo(currPlanParamsYaml, vars);
            } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.nextUtil())!=null);

            //noinspection unchecked,UnnecessaryLocalVariable
            T p = (T)currPlanParamsYaml;

            return p;
        } catch (YAMLException e) {
            throw new WrongVersionOfYamlFileException("Error: " + e.getMessage(), e);
        }
    }


}
