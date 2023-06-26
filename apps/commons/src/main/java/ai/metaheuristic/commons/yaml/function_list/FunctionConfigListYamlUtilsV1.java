/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.function_list;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class FunctionConfigListYamlUtilsV1
        extends AbstractParamsYamlUtils<FunctionConfigListYamlV1, FunctionConfigListYamlV2, FunctionConfigListYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigListYamlV1.class);
    }

    @NonNull
    @Override
    public FunctionConfigListYamlV2 upgradeTo(@NonNull FunctionConfigListYamlV1 src) {
        src.checkIntegrity();
        FunctionConfigListYamlV2 trg = new FunctionConfigListYamlV2();
        trg.functions = src.functions.stream().map(snSrc-> {
            FunctionConfigListYamlV2.FunctionConfigV2 snTrg = new FunctionConfigListYamlV2.FunctionConfigV2();
            BeanUtils.copyProperties(snSrc, snTrg);

            if (snSrc.checksumMap!=null) {
                snTrg.checksumMap = new HashMap<>(snSrc.checksumMap);
            }
            if (snSrc.metas!=null) {
                snTrg.metas = new ArrayList<>(snSrc.metas);
            }
            return  snTrg;
        }).collect(Collectors.toList());
        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public FunctionConfigListYamlUtilsV2 nextUtil() {
        return (FunctionConfigListYamlUtilsV2) FunctionConfigListYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull FunctionConfigListYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionConfigListYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final FunctionConfigListYamlV1 p = getYaml().load(yaml);
        return p;
    }

}
