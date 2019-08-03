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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYaml;
import ai.metaheuristic.api.data.atlas.AtlasTaskParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class AtlasTaskParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<AtlasTaskParamsYamlV1, AtlasTaskParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(AtlasTaskParamsYamlV1.class);
    }

    @Override
    public AtlasTaskParamsYaml upgradeTo(AtlasTaskParamsYamlV1 src) {
        src.checkIntegrity();
        AtlasTaskParamsYaml trg = new AtlasTaskParamsYaml();
        BeanUtils.copyProperties(src, trg);
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(AtlasTaskParamsYamlV1 planYaml) {
        return getYaml().dump(planYaml);
    }

    public AtlasTaskParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final AtlasTaskParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
