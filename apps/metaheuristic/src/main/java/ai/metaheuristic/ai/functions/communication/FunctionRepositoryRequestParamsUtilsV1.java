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

package ai.metaheuristic.ai.functions.communication;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Sergio Lissner
 * Date: 11/15/2023
 * Time: 7:09 PM
 */
public class FunctionRepositoryRequestParamsUtilsV1
    extends AbstractParamsYamlUtils<FunctionRepositoryRequestParamsV1, FunctionRepositoryRequestParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionRepositoryRequestParamsV1.class);
    }

    @NonNull
    @Override
    public FunctionRepositoryRequestParams upgradeTo(@NonNull FunctionRepositoryRequestParamsV1 src) {
        src.checkIntegrity();
        FunctionRepositoryRequestParams trg = new FunctionRepositoryRequestParams();
        trg.processorId = src.processorId;
        trg.functionCodes = src.functionCodes;

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
    public String toString(@NonNull FunctionRepositoryRequestParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionRepositoryRequestParamsV1 to(@NonNull String s) {
        final FunctionRepositoryRequestParamsV1 p = getYaml().load(s);
        return p;
    }

}
