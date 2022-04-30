/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.yaml.core_status;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

public class CoreStatusYamlUtilsV1
        extends AbstractParamsYamlUtils<CoreStatusYamlV1, CoreStatusYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(CoreStatusYamlV1.class);
    }

    @NonNull
    @Override
    public CoreStatusYaml upgradeTo(@NonNull CoreStatusYamlV1 src) {
        src.checkIntegrity();
        CoreStatusYaml trg = new CoreStatusYaml();
        trg.currDir = src.currDir;
        trg.tags = src.tags;
        trg.taskIds = src.taskIds;
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
    public String toString(@NonNull CoreStatusYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public CoreStatusYamlV1 to(@NonNull String s) {
        if (S.b(s)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final CoreStatusYamlV1 p = getYaml().load(s);
        return p;
    }

}
