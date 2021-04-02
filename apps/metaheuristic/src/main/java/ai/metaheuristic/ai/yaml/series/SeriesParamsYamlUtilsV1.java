/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 1:48 PM
 */
public class SeriesParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<SeriesParamsYamlV1, SeriesParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SeriesParamsYamlV1.class);
    }

    @NonNull
    @Override
    public SeriesParamsYaml upgradeTo(@NonNull SeriesParamsYamlV1 src) {
        src.checkIntegrity();
        SeriesParamsYaml trg = new SeriesParamsYaml();

        trg.hyperParams.putAll(src.hyperParams);
        trg.fitting = src.fitting;
        trg.metrics.values.putAll(src.metrics.values);
        trg.variables.addAll(src.variables);

        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull SeriesParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public SeriesParamsYamlV1 to(@NonNull String s) {
        final SeriesParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
