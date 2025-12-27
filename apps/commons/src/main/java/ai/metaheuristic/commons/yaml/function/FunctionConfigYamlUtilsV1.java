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
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtils;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtilsV2;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;

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

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigYamlV1.class);
    }

    @NonNull
    @Override
    public FunctionConfigYamlV2 upgradeTo(@NonNull FunctionConfigYamlV1 src) {
        src.checkIntegrity();
        FunctionConfigYamlV2 trg = new FunctionConfigYamlV2();
        BeanUtils.copyProperties(src, trg.function, "checksumMap", "metas");

        if (src.checksumMap!=null) {
            trg.system.checksumMap.putAll(src.checksumMap);
        }
        trg.function.metas = new ArrayList<>();
        if (src.metas!=null) {
            trg.function.metas.addAll(src.metas);
        }
        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public FunctionConfigYamlUtilsV2 nextUtil() {
        return (FunctionConfigYamlUtilsV2) FunctionConfigYamlUtils.UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull FunctionConfigYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionConfigYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final FunctionConfigYamlV1 p = getYaml().load(yaml);
        return p;
    }

}
