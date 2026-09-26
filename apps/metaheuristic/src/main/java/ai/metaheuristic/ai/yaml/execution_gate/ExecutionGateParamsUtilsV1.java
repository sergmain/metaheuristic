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

package ai.metaheuristic.ai.yaml.execution_gate;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class ExecutionGateParamsUtilsV1
        extends AbstractParamsYamlUtils<
    ExecutionGateParamsV1, ExecutionGateParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecutionGateParamsV1.class);
    }

    @NonNull
    @Override
    public ExecutionGateParams upgradeTo(@NonNull ExecutionGateParamsV1 v1) {
        ExecutionGateParams t = new ExecutionGateParams();
        t.triggeredByTaskId = v1.triggeredByTaskId;
        t.functionCode = v1.functionCode;
        t.processorId = v1.processorId;
        t.matchedPattern = v1.matchedPattern;
        t.consoleExcerpt = v1.consoleExcerpt;
        t.incrementTries = v1.incrementTries;
        return t;
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
    public String toString(@NonNull ExecutionGateParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecutionGateParamsV1 to(@NonNull String s) {
        final ExecutionGateParamsV1 p = getYaml().load(s);
        return p;
    }
}
