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

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ <a href="https://github.com/sergmain/metaheuristic/wiki/multi-versioning-mechanic">...</a></b>
 * <br/>
 *
 * <p>mh-api-analyzers.yaml - analyzer rules shipped with MH itself, read once when the Dispatcher starts
 * and applied to EVERY Function exactly as a Function's own {@code analyzers} block is applied: the same
 * rule type, the same validation, the same matching. See {@code ExecutionGateService#analyzersOf}.
 *
 * <p>❗ Validated with the rules a Function descriptor is held to
 * ({@link FunctionAnalyzerUtils#checkDeclared}): no dispatcher-only scope. Rules that need {@code global}
 * or {@code company} scope belong to whoever runs the Dispatcher - {@code mh.dispatcher.execution-gate.analyzers}
 * in application.properties - not to MH's own defaults.
 *
 * <p>Error code prefix: {@code 01.956.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 10/26/2026
 */
@Data
public class ApiAnalyzersYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        final List<String> errors = analyzers.stream()
                .flatMap(a -> FunctionAnalyzerUtils.checkDeclared(a).stream())
                .toList();
        if (!errors.isEmpty()) {
            throw new CheckIntegrityFailedException("01.956.020 mh-api-analyzers.yaml declares broken analyzer rules: " + errors);
        }
        return true;
    }

    public List<FunctionConfigYaml.Analyzer> analyzers = new ArrayList<>();
}
