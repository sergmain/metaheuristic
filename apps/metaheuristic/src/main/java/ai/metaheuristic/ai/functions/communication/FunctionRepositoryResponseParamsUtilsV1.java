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

package ai.metaheuristic.ai.functions.communication;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 11/15/2023
 * Time: 7:09 PM
 */
public class FunctionRepositoryResponseParamsUtilsV1
    extends AbstractParamsYamlUtils<FunctionRepositoryResponseParamsV1, FunctionRepositoryResponseParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionRepositoryResponseParamsV1.class);
    }

    @NonNull
    @Override
    public FunctionRepositoryResponseParams upgradeTo(@NonNull FunctionRepositoryResponseParamsV1 src) {
        src.checkIntegrity();

        FunctionRepositoryResponseParams trg = new FunctionRepositoryResponseParams();
        trg.success = src.success;
        trg.functions = to(src.functions);

        trg.checkIntegrity();
        return trg;
    }

    @Nullable
    public static List<FunctionRepositoryResponseParams.ShortFunctionConfig> to(@Nullable List<FunctionRepositoryResponseParamsV1.ShortFunctionConfigV1> v1) {
        if (v1==null) {
            return null;
        }
        FunctionRepositoryResponseParams v = new FunctionRepositoryResponseParams();
        v.functions = v1.stream().map(o->new FunctionRepositoryResponseParams.ShortFunctionConfig(o.code, o.sourcing, o.git)).toList();
        return v.functions;
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
    public String toString(@NonNull FunctionRepositoryResponseParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionRepositoryResponseParamsV1 to(@NonNull String s) {
        final FunctionRepositoryResponseParamsV1 p = getYaml().load(s);
        return p;
    }

}
