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
import org.jspecify.annotations.Nullable;
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

    private static FunctionConfigYaml cfg(EnumsApi.FunctionSourcing sourcing, @Nullable String declared) {
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
        assertEquals("ASSETS",
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
        assertEquals("ALL",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "ALL"),
            "ALL removes the whole task dir, which covers the asset dir - the descriptor's choice stands");
        assertEquals("ASSETS",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "ASSETS"));
        assertEquals("ALL",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.dispatcher, "ALL"));
    }

    @Test
    public void test_gitSourcedListWithAssets_isKeptInCanonicalForm() {
        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "ASSETS, FULL_AFTER_ONE_WEEK"));
        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "FULL_AFTER_ONE_WEEK,ASSETS"),
            "the order of the enum, joined by ', '");
    }

    @Test
    public void test_gitSourcedListWithoutAssetsOrAll_getsAssets() {
        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "FULL_AFTER_ONE_WEEK"),
            "FULL_AFTER_ONE_WEEK alone would keep the asset dir of a git-sourced Function for a week");
    }

    @Test
    public void test_gitSourcedListWithAll_getsNoAssets() {
        assertEquals("ALL, FULL_AFTER_ONE_WEEK",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "FULL_AFTER_ONE_WEEK, ALL"));
    }

    @Test
    public void test_dispatcherSourcedList_isKeptAsDeclared() {
        assertEquals("FULL_AFTER_ONE_WEEK",
            TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.dispatcher, "FULL_AFTER_ONE_WEEK"));
    }

    @Test
    public void test_blankList_isTreatedAsNotDeclared() {
        assertEquals("ASSETS", TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, " , "));
        assertNull(TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.dispatcher, ""));
    }

    @Test
    public void test_unknownName_isRefused() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> TaskParamsUtils.defaultCleaningPolicy(EnumsApi.FunctionSourcing.git, "ASSETS, FULL_AFTER_A_WEEK"));
        assertTrue(e.getMessage().contains("FULL_AFTER_A_WEEK"), e.getMessage());
    }

    @Test
    public void test_listReachesTaskConfigThroughToFunctionConfig() {
        final TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(cfg(EnumsApi.FunctionSourcing.git, "FULL_AFTER_ONE_WEEK, ASSETS"));
        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK", fc.cleaningPolicy);
    }

    @Test
    public void test_defaultIsAppliedByToFunctionConfig() {
        final TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(cfg(EnumsApi.FunctionSourcing.git, null));
        assertEquals("ASSETS", fc.cleaningPolicy,
            "the default must reach the task config, not just the helper");
    }

    @Test
    public void test_toFunctionConfigLeavesDispatcherSourcedUnset() {
        final TaskParamsYaml.FunctionConfig fc = TaskParamsUtils.toFunctionConfig(cfg(EnumsApi.FunctionSourcing.dispatcher, null));
        assertNull(fc.cleaningPolicy);
    }
}
