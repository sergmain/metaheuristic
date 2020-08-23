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

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.YamlVersion;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;
import java.util.Objects;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:58 PM
 */
public class BaseYamlUtils<T extends BaseParams> {

    private @NonNull final ParamsYamlUtilsFactory FACTORY;

    public BaseYamlUtils(@NonNull Map<Integer, AbstractParamsYamlUtils> map, @NonNull AbstractParamsYamlUtils defYamlUtils) {
        map.forEach((k,v)-> {
            if (k!=v.getVersion()) {
                throw new IllegalStateException(S.f("Version is different, class: %s", v.getClass()));
            }
        });
        FACTORY = new ParamsYamlUtilsFactory(map, defYamlUtils);;
    }

    public @Nullable AbstractParamsYamlUtils getForVersion(int version) {
        return FACTORY.getForVersion(version);
    }

    public @NonNull AbstractParamsYamlUtils getDefault() {
        return FACTORY.getDefault();
    }

    public @NonNull String toString(@NonNull BaseParams baseParams) {
        baseParams.checkIntegrity();
        return Objects.requireNonNull(getDefault().getYaml().dumpAsMap(baseParams));
    }

    public @NonNull String toStringAsVersion(@NonNull BaseParams baseParamsYaml, int version) {
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

    public T to(@NonNull String s, @Nullable Long ... vars) {
        try {
            YamlVersion v = YamlForVersioning.getYamlVersion(s);
            AbstractParamsYamlUtils yamlUtils = getForVersion(v.getActualVersion());
            if (yamlUtils==null) {
                throw new IllegalStateException("Unsupported version: " + v.getActualVersion());
            }

            BaseParams currBaseParamsYaml = yamlUtils.to(s);
            do {
                //noinspection unchecked
                currBaseParamsYaml = yamlUtils.upgradeTo(currBaseParamsYaml, vars);
            } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.nextUtil())!=null);

            //noinspection unchecked
            T p = (T)currBaseParamsYaml;

            p.checkIntegrity();

            return p;
        } catch (YAMLException e) {
            throw new WrongVersionOfYamlFileException("Error: " + e.getMessage(), e);
        }
    }


}
