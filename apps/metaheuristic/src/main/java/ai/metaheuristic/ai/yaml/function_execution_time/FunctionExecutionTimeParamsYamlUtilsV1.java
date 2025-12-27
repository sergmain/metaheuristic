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

package ai.metaheuristic.ai.yaml.function_execution_time;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class FunctionExecutionTimeParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<FunctionExecutionTimeParamsYamlV1, FunctionExecutionTimeParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(FunctionExecutionTimeParamsYamlV1.class);
    }

    @Override
    public FunctionExecutionTimeParamsYaml upgradeTo(FunctionExecutionTimeParamsYamlV1 v1) {
        v1.checkIntegrity();
        FunctionExecutionTimeParamsYaml t = new FunctionExecutionTimeParamsYaml();
        t.execTime.addAll(v1.execTime);
        t.checkIntegrity();
        return t;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull FunctionExecutionTimeParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public FunctionExecutionTimeParamsYamlV1 to(@NonNull String s) {
        final FunctionExecutionTimeParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
