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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class FunctionConfigYamlUtilsV2
        extends AbstractParamsYamlUtils<FunctionConfigYamlV2, FunctionConfigYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionConfigYamlV2.class);
    }

    @Nonnull
    @Override
    public FunctionConfigYaml upgradeTo(@Nonnull FunctionConfigYamlV2 src) {
        src.checkIntegrity();
        FunctionConfigYaml trg = new FunctionConfigYaml();
        trg.function = to(src.function);
        trg.system = toSystem(src.system);
        trg.checkIntegrity();
        return trg;
    }

    private static FunctionConfigYaml.FunctionConfig  to(FunctionConfigYamlV2.FunctionConfigV2 src) {
        FunctionConfigYaml.FunctionConfig trg = new FunctionConfigYaml.FunctionConfig ();
        BeanUtils.copyProperties(src, trg);

        if (src.metas!=null) {
            trg.metas = new ArrayList<>(src.metas);
        }

        return trg;
    }

    @Nullable
    private static FunctionConfigYaml.System toSystem(@Nullable FunctionConfigYamlV2.SystemV2 src) {
        if (src==null) {
            return null;
        }
        FunctionConfigYaml.System trg = new FunctionConfigYaml.System();
        trg.checksumMap.putAll(src.checksumMap);
        trg.archive = src.archive;
        return trg;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
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
    public String toString(@Nonnull FunctionConfigYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public FunctionConfigYamlV2 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final FunctionConfigYamlV2 p = getYaml().load(yaml);
        return p;
    }

}
