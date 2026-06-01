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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class FunctionConfigYamlUtilsV3
        extends AbstractParamsYamlUtils<FunctionConfigYamlV3, FunctionConfigYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigYamlV3.class);
    }

    @Override
    public FunctionConfigYaml upgradeTo(FunctionConfigYamlV3 src) {
        src.checkIntegrity();
        FunctionConfigYaml trg = new FunctionConfigYaml();
        trg.function = to(src.function);
        // trg was just created so system isn't null
        //noinspection DataFlowIssue
        toSystem(src.system, trg.system);

        trg.checkIntegrity();
        return trg;
    }

    static FunctionConfigYaml.FunctionConfig to(FunctionConfigYamlV3.FunctionConfigV3 src) {
        FunctionConfigYaml.FunctionConfig trg = new FunctionConfigYaml.FunctionConfig();
        BeanUtils.copyProperties(src, trg, "metas", "targets", "api");

        if (src.metas!=null) {
            trg.metas = new ArrayList<>(src.metas);
        }

        if (src.api!=null) {
            trg.api = new FunctionConfigYaml.Api(src.api.keyCode);
        }

        trg.targets = new LinkedHashMap<>();
        for (Map.Entry<String, FunctionConfigYamlV3.TargetV3> e : src.targets.entrySet()) {
            trg.targets.put(e.getKey(), new FunctionConfigYaml.Target(e.getValue().src, e.getValue().file));
        }

        return trg;
    }

    private static void toSystem(FunctionConfigYamlV3.SystemV3 src, FunctionConfigYaml.System trg) {
        trg.checksumMap.putAll(src.checksumMap);
        trg.archive = src.archive;
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
    public String toString(FunctionConfigYamlV3 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public FunctionConfigYamlV3 to(String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final FunctionConfigYamlV3 p = getYaml().load(yaml);
        return p;
    }

}
