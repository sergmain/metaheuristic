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

package ai.metaheuristic.commons.yaml.versioning;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;
import java.util.Objects;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:58 PM
 */
@Slf4j
public class BaseYamlUtils<T extends BaseParams> {

    private final ParamsYamlUtilsFactory FACTORY;

    public BaseYamlUtils(Map<Integer, AbstractParamsYamlUtils> map, AbstractParamsYamlUtils defYamlUtils) {
        map.forEach((k,v)-> {
            if (k!=v.getVersion()) {
                final String es = "Versions are different, class: "+ v.getClass() + ", expected version: "+ k+", version of class: " + v.getVersion();
                log.error(es);
                throw new IllegalStateException(es);
            }
        });
        FACTORY = new ParamsYamlUtilsFactory(map, defYamlUtils);;
    }

    public AbstractParamsYamlUtils getForVersion(int version) {
        return FACTORY.getForVersion(version);
    }

    public AbstractParamsYamlUtils getDefault() {
        return FACTORY.getDefault();
    }

    public String toString(BaseParams baseParams) {
        baseParams.checkIntegrity();
        return Objects.requireNonNull(getDefault().getYaml().dumpAsMap(baseParams));
    }

    public String toStringAsVersion(BaseParams baseParamsYaml, int version) {
        if (baseParamsYaml.getVersion()==version) {
            return toString(baseParamsYaml);
        }

        AbstractParamsYamlUtils utils = getForVersion(version);
        if (utils==null) {
            throw new IllegalStateException("Unsupported version: " + version);
        }
        if (getDefault().getVersion()==version) {
            return toString(baseParamsYaml);
        }
        else {

            AbstractParamsYamlUtils yamlUtils = getDefault();
            Object currBaseParamsYaml = baseParamsYaml;
            do {
                if (yamlUtils.getVersion()==version) {
                    break;
                }
                //noinspection unchecked
                currBaseParamsYaml = yamlUtils.downgradeTo(currBaseParamsYaml);
            } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.prevUtil())!=null);

            //noinspection unchecked
            T p = (T)currBaseParamsYaml;

            return Objects.requireNonNull(utils.getYaml().dumpAsMap(p));
        }
    }

    public T to(String s) {
        try {
            ParamsVersion v = YamlForVersioning.getParamsVersion(s);
            AbstractParamsYamlUtils yamlUtils = getForVersion(v.getActualVersion());
            if (yamlUtils==null) {
                throw new IllegalStateException("Unsupported version: " + v.getActualVersion());
            }

            BaseParams currBaseParamsYaml = yamlUtils.to(s);
            do {
                //noinspection unchecked
                currBaseParamsYaml = yamlUtils.upgradeTo(currBaseParamsYaml);
            } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.nextUtil())!=null);

            //noinspection unchecked
            T p = (T)currBaseParamsYaml;

            p.checkIntegrity();

            return p;
        } catch (YAMLException e) {
            throw new WrongVersionOfParamsException("Error: " + e.getMessage(), e);
        }
    }


}
