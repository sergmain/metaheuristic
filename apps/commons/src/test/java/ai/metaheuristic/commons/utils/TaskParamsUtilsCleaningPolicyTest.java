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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 10:20 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TaskParamsUtilsCleaningPolicyTest {

    private static FunctionConfigYaml cfg(EnumsApi.FunctionSourcing sourcing, EnumsApi.CleaningPolicy declared) {
        final FunctionConfigYaml c = new FunctionConfigYaml();
        c.function.code = "fn-py";
        c.function.sourcing = sourcing;
        c.function.cleaningPolicy = declared;
        if (sourcing==EnumsApi.FunctionSourcing.git) {
            c.function.git = new GitInfo("https://example.com/r.git", "main", "HEAD", "fn");
        }
        return c;
    }

    @Test
    public void test_gitSourcedWithNoPolicyDefaultsToAssets() {
        assertEquals(EnumsApi.CleaningPolicy.ASSETS,
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, null));
    }

    @Test
    public void test_dispatcherSourcedWithNoPolicyStaysUnset() {
        assertNull(TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.dispatcher, null));
    }

    @Test
    public void test_nullSourcingWithNoPolicyStaysUnset() {
        assertNull(TaskParamsUtils.defaultCleaningPolicy(null, null));
    }

    @Test
    public void test_anExplicitPolicyAlwaysWins() {
        assertEquals(EnumsApi.CleaningPolicy.ALL,
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, EnumsApi.CleaningPolicy.ALL),
            "ALL removes the whole task dir, which covers the asset dir - the descriptor's choice stands");
        assertEquals(EnumsApi.CleaningPolicy.ASSETS,
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, EnumsApi.CleaningPolicy.ASSETS));
        assertEquals(EnumsApi.CleaningPolicy.ALL,
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.dispatcher, EnumsApi.CleaningPolicy.ALL));
    }

    @Test
    public void test_defaultIsAppliedByToFunctionConfig() {
        final TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(cfg(EnumsApi.FunctionSourcing.git, null));
        assertEquals(EnumsApi.CleaningPolicy.ASSETS, fc.cleaningPolicy,
            "the default must reach the task config, not just the helper");
    }

    @Test
    public void test_toFunctionConfigLeavesDispatcherSourcedUnset() {
        final TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(cfg(EnumsApi.FunctionSourcing.dispatcher, null));
        assertNull(fc.cleaningPolicy);
    }
}
