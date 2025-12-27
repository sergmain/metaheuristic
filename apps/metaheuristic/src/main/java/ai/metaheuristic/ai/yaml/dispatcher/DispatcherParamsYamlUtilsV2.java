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

package ai.metaheuristic.ai.yaml.dispatcher;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/16/2021
 * Time: 1:06 AM
 */
public class DispatcherParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<DispatcherParamsYamlV2, DispatcherParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherParamsYamlV2.class);
    }

    @NonNull
    @Override
    public DispatcherParamsYaml upgradeTo(@NonNull DispatcherParamsYamlV2 v1) {
        DispatcherParamsYaml t = new DispatcherParamsYaml();

        // stream is being used for possible future extension
        v1.batches.stream().collect(Collectors.toCollection(()->t.batches));
        v1.experiments.stream().collect(Collectors.toCollection(()->t.experiments));
        v1.longRunnings.stream().map(DispatcherParamsYamlUtilsV2::toLongRunning).collect(Collectors.toCollection(()->t.longRunnings));

        return t;
    }

    private static DispatcherParamsYaml.LongRunningExecContext toLongRunning(DispatcherParamsYamlV2.LongRunningExecContextV2 o) {
        return new DispatcherParamsYaml.LongRunningExecContext(o.taskId, o.execContextId);
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
    public String toString(@NonNull DispatcherParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public DispatcherParamsYamlV2 to(@NonNull String s) {
        final DispatcherParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
