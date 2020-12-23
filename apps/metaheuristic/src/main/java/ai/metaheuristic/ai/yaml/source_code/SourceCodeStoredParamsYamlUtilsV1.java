/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYamlV1;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 */
public class SourceCodeStoredParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<SourceCodeStoredParamsYamlV1, SourceCodeStoredParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeStoredParamsYamlV1.class);
    }

    @NonNull
    @Override
    public SourceCodeStoredParamsYaml upgradeTo(@NonNull SourceCodeStoredParamsYamlV1 v1, @Nullable Long ... vars) {
        v1.checkIntegrity();
        SourceCodeStoredParamsYaml p = new SourceCodeStoredParamsYaml();
        p.internalParams.init(v1.internalParams.archived, v1.internalParams.published, v1.internalParams.updatedOn, null);
        p.source = v1.source;
        p.lang = v1.lang;
        p.checkIntegrity();
        return p;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
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
    public String toString(@NonNull SourceCodeStoredParamsYamlV1 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @NonNull
    @Override
    public SourceCodeStoredParamsYamlV1 to(@NonNull String s) {
        final SourceCodeStoredParamsYamlV1 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}
