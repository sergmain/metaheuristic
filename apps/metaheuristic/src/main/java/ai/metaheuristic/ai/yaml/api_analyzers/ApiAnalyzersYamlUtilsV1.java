/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.api_analyzers;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;

/**
 * V1 of the mh-api-analyzers.yaml chain, and currently its head: it upgrades V1 straight to the version-less
 * {@link ApiAnalyzersYaml}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.957.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 10/26/2026
 */
public class ApiAnalyzersYamlUtilsV1
        extends AbstractParamsYamlUtils<ApiAnalyzersYamlV1, ApiAnalyzersYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiAnalyzersYamlV1.class);
    }

    @NonNull
    @Override
    public ApiAnalyzersYaml upgradeTo(@NonNull ApiAnalyzersYamlV1 src) {
        src.checkIntegrity();
        final ApiAnalyzersYaml trg = new ApiAnalyzersYaml();

        // mapped element by element: the element types differ per version, so a shallow copy would put V1
        // objects into a version-less list. An 'analyzers:' key with nothing under it loads as null and
        // means "no rules".
        if (src.analyzers != null) {
            for (ApiAnalyzersYamlV1.AnalyzerV1 a : src.analyzers) {
                final ArrayList<String> regex = a.regex != null ? new ArrayList<>(a.regex) : new ArrayList<>();
                trg.analyzers.add(new FunctionConfigYaml.Analyzer(a.name, regex, a.timeout, a.incrementTries, a.scope));
            }
        }

        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        throw new DowngradeNotSupportedException();
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
    public String toString(@NonNull ApiAnalyzersYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ApiAnalyzersYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("01.957.020 mh-api-analyzers.yaml is blank");
        }
        return getYaml().load(yaml);
    }
}
