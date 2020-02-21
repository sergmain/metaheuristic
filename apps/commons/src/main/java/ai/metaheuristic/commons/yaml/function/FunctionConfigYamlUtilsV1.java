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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class FunctionConfigYamlUtilsV1
        extends AbstractParamsYamlUtils<FunctionConfigYamlV1, FunctionConfigYamlV2, FunctionConfigYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigYamlV1.class);
    }

    @Override
    public FunctionConfigYamlV2 upgradeTo(FunctionConfigYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        FunctionConfigYamlV2 trg = new FunctionConfigYamlV2();
        BeanUtils.copyProperties(src, trg);

        if (src.checksumMap!=null) {
            trg.checksumMap = new HashMap<>(src.checksumMap);
        }
        if (src.info!=null) {
            trg.info = new FunctionConfigYamlV2.FunctionInfoV2(src.info.signed, src.info.length);
        }
        if (src.metas!=null) {
            trg.metas = new ArrayList<>(src.metas);
        }
        if (src.metrics) {
            trg.ml = new FunctionConfigYamlV2.MachineLearningV2(true, false);
        }
        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public FunctionConfigYamlUtilsV2 nextUtil() {
        return (FunctionConfigYamlUtilsV2) FunctionConfigYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(FunctionConfigYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public FunctionConfigYamlV1 to(String s) {
        if (S.b(s)) {
            return null;
        }
        //noinspection UnnecessaryLocalVariable
        final FunctionConfigYamlV1 p = getYaml().load(s);
        return p;
    }

}
