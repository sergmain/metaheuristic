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

package ai.metaheuristic.commons.yaml.bundle_cfg;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 8/19/2020
 * Time: 3:39 AM
 */
@Data
public class BundleCfgYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        // ❗ Bundle-level rules are checked at parse, for the reason FunctionConfigYaml checks a Function's
        // own rules there: a rule that can never fire would otherwise load cleanly, and the bundle author
        // would believe it was in force for every Function of the bundle.
        if (analyzers!=null) {
            final List<String> errors = analyzers.stream()
                    .flatMap(a -> FunctionAnalyzerUtils.checkDeclared(a).stream())
                    .toList();
            if (!errors.isEmpty()) {
                throw new CheckIntegrityFailedException("bundle-level analyzers: " + errors);
            }
        }
        return true;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class BundleConfig {
        public String path;
        public EnumsApi.BundleItemType type;
    }

    public final List<BundleConfig> bundleConfig = new ArrayList<>();

    /**
     * Analyzer rules for EVERY Function of this bundle, in the same form as a Function's own
     * {@code analyzers} block. Applied when the bundle is packaged (BundleUtils.processFunctions), after
     * the Function's own rules - see FunctionAnalyzerUtils.withBundleAnalyzers - so the descriptor that
     * reaches the Dispatcher already carries them and nothing downstream knows where a rule came from.
     *
     * <p>@Nullable per the @Nullable-exception rule of the multi-versioning mechanic - no version bump.
     * null means the bundle declares none, which is the normal case.
     */
    public @Nullable List<FunctionConfigYaml.Analyzer> analyzers;
}

